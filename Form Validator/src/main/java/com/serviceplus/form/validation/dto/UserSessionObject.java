package com.serviceplus.form.validation.dto;

import java.io.Serializable;
import java.util.Date;
import java.util.List;


public class UserSessionObject implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Long userID;
	private Date lastCheck;
	private String signNo;
	private Integer clcId;
	private List<Roles> roles;
	private String userIdentifier;
	private String emailId;
	private String jwt;
	private Integer locationId;
	private String locationName;
	private Integer entityId;
	private String clcName;
	private String mobileNo;
	private String userName;
	private Integer designationId;
	private Integer entityLevelId;
	private String entityLevelName;
    private Integer categoryId;
    private String tenantId;
    private Boolean isTopLevelAdmin;
    private String atk;
    private String csrfToken;
    private Boolean active;


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

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public String getSignNo() {
		return signNo;
	}


	public void setSignNo(String signNo) {
		this.signNo = signNo;
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

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Boolean getTopLevelAdmin() {
        return isTopLevelAdmin;
    }

    public void setTopLevelAdmin(Boolean topLevelAdmin) {
        isTopLevelAdmin = topLevelAdmin;
    }

    public String getAtk() {
        return atk;
    }

    public void setAtk(String atk) {
        this.atk = atk;
    }

    public Integer getClcId() {
        return clcId;
    }

    public void setClcId(Integer clcId) {
        this.clcId = clcId;
    }

    public Integer getEntityId() {
        return entityId;
    }

    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    public String getClcName() {
        return clcName;
    }

    public void setClcName(String clcName) {
        this.clcName = clcName;
    }

    public Integer getEntityLevelId() {
        return entityLevelId;
    }

    public void setEntityLevelId(Integer entityLevelId) {
        this.entityLevelId = entityLevelId;
    }

    public String getEntityLevelName() {
        return entityLevelName;
    }

    public void setEntityLevelName(String entityLevelName) {
        this.entityLevelName = entityLevelName;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public Long getUserID() {
        return userID;
    }

    public void setUserID(Long userID) {
        this.userID = userID;
    }

    public String getCsrfToken() {
        return csrfToken;
    }

    public void setCsrfToken(String csrfToken) {
        this.csrfToken = csrfToken;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
