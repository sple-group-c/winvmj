package MeetingManagementVM.meetingmanagement.projectmeetingdelta.model;

import java.util.*;

import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import MeetingManagementVM.meetingmanagement.core.model.MeetingManagementComponent;
import MeetingManagementVM.meetingmanagement.core.model.MeetingManagementDecorator;
import ProjectVM.project.core.model.Project;
import ProjectVM.project.core.model.ProjectComponent;

import ProjectVM.project.core.Project;

@Entity(name="meetingmanagement_projectmeetingdelta")
@Table(name="meetingmanagement_projectmeetingdelta")
public class MeetingManagementImpl extends MeetingManagementDecorator {

	@ManyToOne(targetEntity = ProjectComponent.class)
	@JoinColumn(name = "idProject", referencedColumnName = "idProject")
	protected Project project;

	public MeetingManagementImpl() {
		super();
		this.objectName = MeetingManagementImpl.class.getName();
	}

	public MeetingManagementImpl(MeetingManagementComponent record, Project project) {
		super(record, MeetingManagementImpl.class.getName());
		this.project = project;
		this.objectName = MeetingManagementImpl.class.getName();
	}

	public Project getProject() {
		return this.project;
	}

	public void setProject(Project project) {
		this.project = project;
	}

	public HashMap<String, Object> toHashMap() {
		HashMap<String, Object> map = record.toHashMap();
		map.put("idMeeting", getIdMeeting());
		map.put("project", project != null ? project.toHashMap() : null);

		return map;
	}
}
