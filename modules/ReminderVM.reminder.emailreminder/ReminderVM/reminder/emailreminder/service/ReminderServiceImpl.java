package ReminderVM.reminder.emailreminder.service;

import java.util.*;
import java.lang.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import id.ac.ui.cs.prices.winvmj.core.VMJExchange;

import ReminderVM.reminder.core.service.ReminderServiceDecorator;
import ReminderVM.reminder.core.model.ReminderImpl;
import ReminderVM.reminder.core.service.ReminderServiceComponent;
import ReminderVM.reminder.core.model.Reminder;
import ReminderVM.reminder.core.model.ReminderDecorator;
import ReminderVM.reminder.ReminderFactory;

public class ReminderServiceImpl extends ReminderServiceDecorator {

	private static final Logger logger = Logger.getLogger(ReminderServiceImpl.class.getName());

	private static final ScheduledExecutorService scheduler =
		Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "reminder-email-scheduler");
			t.setDaemon(true);
			return t;
		});
	private static final Map<Integer, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();
	private static volatile boolean schedulerStarted = false;

	public ReminderServiceImpl(ReminderServiceComponent record) {
		super(record);
		startSchedulerIfNeeded(this);
	}

	private static synchronized void startSchedulerIfNeeded(ReminderServiceImpl instance) {
		if (!schedulerStarted) {
			schedulerStarted = true;
			// Small delay lets Hibernate finish initializing before we query the DB
			scheduler.schedule(instance::rescheduleAllReminders, 5, TimeUnit.SECONDS);
			logger.info("Reminder email scheduler started.");
		}
	}

	// Called once on startup to restore scheduled futures from persisted reminders
	private void rescheduleAllReminders() {
		try {
			List<Reminder> reminders = Repository.getAllObject("reminder_emailreminder");
			for (Reminder r : reminders) {
				scheduleReminderEmail((ReminderVM.reminder.emailreminder.model.ReminderImpl) r);
			}
			logger.info("Scheduled " + reminders.size() + " reminder(s) from DB.");
		} catch (Exception e) {
			logger.severe("Failed to restore reminders on startup: " + e.getMessage());
		}
	}

	private void scheduleReminderEmail(ReminderVM.reminder.emailreminder.model.ReminderImpl reminder) {
		if (reminder.getIsDisabled()) return;

		int id = reminder.getIdReminder();
		cancelScheduledReminder(id);

		// First schedule: we don't know if today's HH:MM already passed, so check.
		// If it has, target tomorrow. If not, fire later today.
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime nextFire = now
			.withHour(reminder.getHour())
			.withMinute(reminder.getMinute())
			.withSecond(0)
			.withNano(0);
		if (!nextFire.isAfter(now)) {
			nextFire = nextFire.plusDays(1);
		}
		long delaySeconds = Duration.between(now, nextFire).getSeconds();

		ScheduledFuture<?> future = scheduler.schedule(() -> {
			sendReminderNotification(reminder);
			rescheduleNextDay(reminder); 
		}, delaySeconds, TimeUnit.SECONDS);

		scheduledFutures.put(id, future);
		logger.info(String.format("Reminder #%d will fire in %ds (at %02d:%02d daily).",
			id, delaySeconds, reminder.getHour(), reminder.getMinute()));
	}

	private void rescheduleNextDay(ReminderVM.reminder.emailreminder.model.ReminderImpl reminder) {
		if (reminder.getIsDisabled()) return;

		int id = reminder.getIdReminder();
		cancelScheduledReminder(id);

		// Anchor to tomorrow's exact wall-clock HH:MM 
		// This corrects any accumulated drift — if we fired 2s late, the next delay
		// is 24h - 2s, keeping the fire time pinned to the same minute each day.
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime nextFire = now.plusDays(1)
			.withHour(reminder.getHour())
			.withMinute(reminder.getMinute())
			.withSecond(0)
			.withNano(0);
		long delaySeconds = Duration.between(now, nextFire).getSeconds();

		ScheduledFuture<?> future = scheduler.schedule(() -> {
			sendReminderNotification(reminder);
			rescheduleNextDay(reminder);
		}, delaySeconds, TimeUnit.SECONDS);

		scheduledFutures.put(id, future);
	}

	private void cancelScheduledReminder(int reminderId) {
		ScheduledFuture<?> existing = scheduledFutures.remove(reminderId);
		if (existing != null) {
			existing.cancel(false);
		}
	}

	private void sendReminderNotification(ReminderVM.reminder.emailreminder.model.ReminderImpl reminder) {
		// Self-heal: task deletion cascades at DB level, so the reminder row may be gone
		try {
			if (Repository.getObject(reminder.getIdReminder()) == null) {
				scheduledFutures.remove(reminder.getIdReminder());
				logger.info("Reminder #" + reminder.getIdReminder() + " no longer exists; stopping future.");
				return;
			}
		} catch (Exception e) {
			scheduledFutures.remove(reminder.getIdReminder());
			return;
		}

		String smtpApiUrl = System.getenv("SMTP_API_URL");
		String smtpApiKey = System.getenv("SMTP_API_KEY");
		String fromEmail = System.getenv().getOrDefault("SMTP_FROM", "no-reply@remindervm.com");

		if (smtpApiUrl == null || smtpApiUrl.isEmpty()) {
			logger.info("SMTP_API_URL not configured; skipping email to: " + reminder.getEmail());
			return;
		}

		try {
			String text = String.format(
				"Reminder for task #%d at %02d:%02d.",
				reminder.getRemindingForId(), reminder.getHour(), reminder.getMinute()
			);
			String payload = buildSendGridPayload(fromEmail, reminder.getEmail(), "Reminder Notification", text);

			HttpClient client = HttpClient.newHttpClient();
			HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
				.uri(URI.create(smtpApiUrl))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(payload));
			if (smtpApiKey != null && !smtpApiKey.isEmpty()) {
				reqBuilder.header("Authorization", "Bearer " + smtpApiKey);
			}
			HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				logger.warning("Email API returned " + response.statusCode() + ": " + response.body());
			} else {
				logger.info("Reminder email sent to: " + reminder.getEmail());
			}
		} catch (Exception e) {
			logger.severe("Failed to send reminder email: " + e.getMessage());
		}
	}

	// SendGrid v3 /mail/send payload format
	private String buildSendGridPayload(String from, String to, String subject, String text) {
		return String.format(
			"{\"personalizations\":[{\"to\":[{\"email\":\"%s\"}]}]," +
			"\"from\":{\"email\":\"%s\"}," +
			"\"subject\":\"%s\"," +
			"\"content\":[{\"type\":\"text/plain\",\"value\":\"%s\"}]}",
			to, from, subject, text
		);
	}

 	public Reminder createReminder(Map<String, Object> requestBody){
		String email = (String) requestBody.get("email");
		boolean isDisabled = (boolean) requestBody.get("isDisabled");
		String hourStr = (String) requestBody.get("hour");
		int hour = Integer.parseInt(hourStr);
		String minuteStr = (String) requestBody.get("minute");
		int minute = Integer.parseInt(minuteStr);
		String remindingForIdStr = (String) requestBody.get("remindingForId");
		int remindingForId = Integer.parseInt(remindingForIdStr);
		Reminder reminderemailreminder = record.createReminder(requestBody);
		Reminder reminderemailreminderdeco = ReminderFactory.createReminder("ReminderVM.reminder.emailreminder.model.ReminderImpl", reminderemailreminder, email);
		Repository.saveObject(reminderemailreminderdeco);
		scheduleReminderEmail((ReminderVM.reminder.emailreminder.model.ReminderImpl) reminderemailreminderdeco);
		return reminderemailreminderdeco;
	}

	public Reminder createReminder(Map<String, Object> requestBody, int id){
		Reminder savedReminder = Repository.getObject(id);
		String email = (String) requestBody.get("email");
		int recordReminderIdReminder = ((ReminderDecorator) savedReminder).getIdReminder();
		Reminder reminder = record.createReminder(requestBody, recordReminderIdReminder);
		Reminder reminderemailreminder = ReminderFactory.createReminder("ReminderVM.reminder.emailreminder.ReminderImpl", reminder, email);
		return reminderemailreminder;
	}

    public HashMap<String, Object> updateReminder(Map<String, Object> requestBody){
		String idStr = (String) requestBody.get("idReminder");
		int id = Integer.parseInt(idStr);

		Reminder reminderemailreminder = Repository.getObject(id);
		reminderemailreminder = createReminder(requestBody, id);

		Repository.updateObject(reminderemailreminder);
		reminderemailreminder = Repository.getObject(id);

		// Reflect isDisabled change in the scheduler
		ReminderVM.reminder.emailreminder.model.ReminderImpl updated =
			(ReminderVM.reminder.emailreminder.model.ReminderImpl) reminderemailreminder;
		if (updated.getIsDisabled()) {
			cancelScheduledReminder(id);
		} else {
			scheduleReminderEmail(updated);
		}

		return reminderemailreminder.toHashMap();
	}

	public HashMap<String, Object> getReminder(String idStr){
		int id = Integer.parseInt(idStr);
		Reminder reminderemailreminder = Repository.getObject(id);
		return reminderemailreminder.toHashMap();
	}

	public HashMap<String, Object> getReminderById(int id){
		List<HashMap<String, Object>> reminderList = getAllReminder();
		for (HashMap<String, Object> reminder : reminderList){
			int reminder_id = ((Double) reminder.get("idreminder")).intValue();
			if (reminder_id == id){
				return reminder;
			}
		}
		return null;
	}

    public List<HashMap<String,Object>> getAllReminder(){
		List<Reminder> List = Repository.getAllObject("reminder_emailreminder");
		return transformListToHashMap(List);
	}

    public List<HashMap<String,Object>> transformListToHashMap(List<Reminder> List){
		List<HashMap<String,Object>> resultList = new ArrayList<HashMap<String,Object>>();
        for(int i = 0; i < List.size(); i++) {
            resultList.add(List.get(i).toHashMap());
        }
        return resultList;
	}

    public List<HashMap<String,Object>> deleteReminder(Map<String, Object> requestBody){
		String idStr = ((String) requestBody.get("idReminder"));
		int id = Integer.parseInt(idStr);
		cancelScheduledReminder(id);
		Repository.deleteObject(id);
		return getAllReminder();
	}

	// Called when a task is deleted to cascade-delete all its reminders
	public List<HashMap<String,Object>> deleteRemindersByTaskId(int taskId) {
		List<Reminder> reminders = Repository.getAllObject("reminder_emailreminder");
		for (Reminder r : reminders) {
			if (r.getRemindingForId() == taskId) {
				cancelScheduledReminder(r.getIdReminder());
				Repository.deleteObject(r.getIdReminder());
			}
		}
		return getAllReminder();
	}

	public void sendsEmail(String emailAddress) {
		String smtpApiUrl = System.getenv("SMTP_API_URL");
		String smtpApiKey = System.getenv("SMTP_API_KEY");
		String fromEmail = System.getenv().getOrDefault("SMTP_FROM", "no-reply@remindervm.com");

		if (smtpApiUrl == null || smtpApiUrl.isEmpty()) {
			logger.info("SMTP_API_URL not configured; skipping email to: " + emailAddress);
			return;
		}

		try {
			String payload = buildSendGridPayload(fromEmail, emailAddress, "Reminder Notification", "You have a scheduled reminder.");
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
				.uri(URI.create(smtpApiUrl))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(payload));
			if (smtpApiKey != null && !smtpApiKey.isEmpty()) {
				reqBuilder.header("Authorization", "Bearer " + smtpApiKey);
			}
			HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				logger.warning("Email API returned " + response.statusCode() + ": " + response.body());
			} else {
				logger.info("Email sent to: " + emailAddress);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to send email to " + emailAddress, e);
		}
	}
}
