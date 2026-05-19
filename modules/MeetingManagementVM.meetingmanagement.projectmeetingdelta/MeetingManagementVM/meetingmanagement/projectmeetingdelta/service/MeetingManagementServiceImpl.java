package MeetingManagementVM.meetingmanagement.projectmeetingdelta.service;

import java.util.*;

import id.ac.ui.cs.prices.winvmj.hibernate.RepositoryUtil;

import MeetingManagementVM.meetingmanagement.MeetingManagementFactory;
import MeetingManagementVM.meetingmanagement.core.model.MeetingManagement;
import MeetingManagementVM.meetingmanagement.core.service.MeetingManagementServiceComponent;
import MeetingManagementVM.meetingmanagement.core.service.MeetingManagementServiceDecorator;

import ProjectVM.project.core.model.Project;
import ProjectVM.project.core.model.ProjectComponent;
import MeetingManagementVM.meetingmanagement.projectmeetingdelta.model.MeetingManagementImpl;

import ProjectVM.project.core.model.Project;
import ProjectVM.project.core.model.ProjectComponent;


public class MeetingManagementServiceImpl extends MeetingManagementServiceDecorator {
	private final RepositoryUtil<Project> projectRepository;

	public MeetingManagementServiceImpl(MeetingManagementServiceComponent record) {
		super(record);
		this.projectRepository = new RepositoryUtil<Project>(ProjectComponent.class);
	}

	public MeetingManagement createMeetingManagement(Map<String, Object> requestBody) {
		Project project = getProjectFromRequest(requestBody);
		MeetingManagement meetingmanagementprojectmeetingdelta = record.createMeetingManagement(requestBody);
		MeetingManagement meetingmanagementprojectmeetingdeltadeco = MeetingManagementFactory.createMeetingManagement(
				"MeetingManagementVM.meetingmanagement.projectmeetingdelta.model.MeetingManagementImpl",
				meetingmanagementprojectmeetingdelta,
				project);
		Repository.saveObject(meetingmanagementprojectmeetingdeltadeco);
		
		return meetingmanagementprojectmeetingdeltadeco;
	}

	public MeetingManagement createMeetingManagement(Map<String, Object> requestBody, int id) {
		MeetingManagement savedMeetingManagement = Repository.getObject(id);
		if (savedMeetingManagement == null) {
			throw new IllegalArgumentException("MeetingManagement with idMeeting " + id + " not found");
		}
		Project project = getProjectFromRequest(requestBody);
		int recordMeetingManagementIdMeeting = savedMeetingManagement.getIdMeeting();
		MeetingManagement meetingManagement = record.createMeetingManagement(requestBody, recordMeetingManagementIdMeeting);
		return MeetingManagementFactory.createMeetingManagement(
				"MeetingManagementVM.meetingmanagement.projectmeetingdelta.model.MeetingManagementImpl",
				meetingManagement,
				project);
	}

	public HashMap<String, Object> updateMeetingManagement(Map<String, Object> requestBody) {
		String idStr = (String) requestBody.get("idMeeting");
		int id = Integer.parseInt(idStr);

		MeetingManagement meetingmanagement = Repository.getObject(id);

		if (meetingmanagement == null) {
			throw new IllegalArgumentException("Not found");
		}

		meetingmanagement.setName((String) requestBody.get("name"));
		meetingmanagement.setStartDate((String) requestBody.get("startDate"));
		meetingmanagement.setEndDate((String) requestBody.get("endDate"));
		meetingmanagement.setLocation((String) requestBody.get("location"));

		Project project = this.getProjectFromRequest(requestBody);
		if (!(meetingmanagement instanceof MeetingManagementImpl)) {
			throw new IllegalArgumentException(
					"MeetingManagement with idMeeting " + id + " is not a projectmeetingdelta record");
		}

		MeetingManagementImpl meetingmanagementprojectmeetingdelta = (MeetingManagementImpl) meetingmanagement;
		meetingmanagementprojectmeetingdelta.setProject(project);

		Repository.updateObject(meetingmanagementprojectmeetingdelta);
		meetingmanagementprojectmeetingdelta = (MeetingManagementImpl) Repository.getObject(id);

		return meetingmanagementprojectmeetingdelta.toHashMap();
	}

	public HashMap<String, Object> getMeetingManagement(String idStr) {
		int id = Integer.parseInt(idStr);
		MeetingManagement meetingmanagementprojectmeetingdelta = Repository.getObject(id);
		return meetingmanagementprojectmeetingdelta.toHashMap();
	}

	public HashMap<String, Object> getMeetingManagementById(int id) {
		List<HashMap<String, Object>> meetingmanagementList = getAllMeetingManagement();
		for (HashMap<String, Object> meetingmanagement : meetingmanagementList) {
			int meetingmanagement_id = ((Number) meetingmanagement.get("idMeeting")).intValue();
			if (meetingmanagement_id == id) {
				return meetingmanagement;
			}
		}
		return null;
	}

	public List<HashMap<String, Object>> getAllMeetingManagement() {
		List<MeetingManagement> list = Repository.getAllObject("meetingmanagement_projectmeetingdelta");
		return transformListToHashMap(list);
	}

	public List<HashMap<String, Object>> transformListToHashMap(List<MeetingManagement> list) {
		List<HashMap<String, Object>> resultList = new ArrayList<HashMap<String, Object>>();
		for (int i = 0; i < list.size(); i++) {
			resultList.add(list.get(i).toHashMap());
		}

		return resultList;
	}

	public List<HashMap<String, Object>> deleteMeetingManagement(Map<String, Object> requestBody) {
		String idStr = ((String) requestBody.get("idMeeting"));
		int id = Integer.parseInt(idStr);
		Repository.deleteObject(id);
		return getAllMeetingManagement();
	}

	private Project getProjectFromRequest(Map<String, Object> requestBody) {
		Object idProjectValue = requestBody.get("idProject");
		if (idProjectValue == null) {
			throw new IllegalArgumentException("idProject is required to save project meeting relation");
		}

		int idProject = Integer.parseInt(idProjectValue.toString());
		Project project = projectRepository.getObject(idProject);
		if (project == null) {
			throw new IllegalArgumentException("Project with idProject " + idProject + " not found");
		}

		return project;
	}
}
