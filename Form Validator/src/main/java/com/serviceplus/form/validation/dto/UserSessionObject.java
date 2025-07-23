package com.serviceplus.form.validation.dto;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

public class UserSessionObject implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Integer userID;
	private Date lastCheck;
	private String signNo;
	private Integer stateId;
	private List<Roles> roles;
	private String userIdentifier;
	private String emailId;
	private String jwt;
	private Integer locationId;
	private String locationName;
	private Integer departmentId;
	private String stateName;
	private String mobileNo;
	private String userName;
	private Integer designationId;
	private Integer departmentLevelId;
	private String departmentLevelName;
	private String csrfToken;
	private String tenantId;
	
	public static class Roles  implements Serializable{
		private static final long serialVersionUID = 1L;
		private int roleId;
		private String roleName;
		
		public Roles() {
			
		}
		public Roles(int roleId, String roleName) {
			super();
			this.roleId = roleId;
			this.roleName = roleName;
		}

		public int getRoleId() {
			return roleId;
		}
		public void setRoleId(int roleId) {
			this.roleId = roleId;
		}
		public String getRoleName() {
			return roleName;
		}
		public void setRoleName(String roleName) {
			this.roleName = roleName;
		}
	}
	
	public Integer getUserID() {
		return userID;
	}

	public void setUserID(Integer userID) {
		this.userID = userID;
	}


	public Date getLastCheck() {
		return lastCheck;
	}


	public void setLastCheck(Date lastCheck) {
		this.lastCheck = lastCheck;
	}


	public Integer getLocationId() {
		return locationId;
	}

	public void setLocationId(Integer locationId) {
		this.locationId = locationId;
	}

	public String getLocationName() {
		return locationName;
	}

	public void setLocationName(String locationName) {
		this.locationName = locationName;
	}

	public Integer getDepartmentId() {
		return departmentId;
	}

	public void setDepartmentId(Integer departmentId) {
		this.departmentId = departmentId;
	}

	public String getStateName() {
		return stateName;
	}

	public void setStateName(String stateName) {
		this.stateName = stateName;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public String getSignNo() {
		return signNo;
	}


	public void setSignNo(String signNo) {
		this.signNo = signNo;
	}


	public Integer getStateId() {
		return stateId;
	}


	public void setStateId(Integer stateId) {
		this.stateId = stateId;
	}


	public List<Roles> getRoles() {
		return roles;
	}


	public void setRoles(List<Roles> roles) {
		this.roles = roles;
	}


	public String getUserIdentifier() {
		return userIdentifier;
	}


	public void setUserIdentifier(String userIdentifier) {
		this.userIdentifier = userIdentifier;
	}

	public String getEmailId() {
		return emailId;
	}

	public void setEmailId(String emailId) {
		this.emailId = emailId;
	}

	public String getMobileNo() {
		return mobileNo;
	}

	public void setMobileNo(String mobileNo) {
		this.mobileNo = mobileNo;
	}

	public String getJwt() {
		return jwt;
	}


	public void setJwt(String jwt) {
		this.jwt = jwt;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public Integer getDesignationId() {
		return designationId;
	}

	public void setDesignationId(Integer designationId) {
		this.designationId = designationId;
	}

	public Integer getDepartmentLevelId() {
		return departmentLevelId;
	}

	public void setDepartmentLevelId(Integer departmentLevelId) {
		this.departmentLevelId = departmentLevelId;
	}

	public String getDepartmentLevelName() {
		return departmentLevelName;
	}

	public void setDepartmentLevelName(String departmentLevelName) {
		this.departmentLevelName = departmentLevelName;
	}

	public String getCsrfToken() {
		return csrfToken;
	}

	public void setCsrfToken(String csrfToken) {
		this.csrfToken = csrfToken;
	}

	public String getTenantId() {
		return tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}
	
}
