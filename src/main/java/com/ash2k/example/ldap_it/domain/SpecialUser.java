package com.ash2k.example.ldap_it.domain;

import java.io.Serializable;

/**
 * Some special user.
 * 
 * @author Mikhail Mazursky
 */
public final class SpecialUser implements Serializable {

	private static final long serialVersionUID = -2894513640064806569L;

	private final String username, special;

	public SpecialUser(String username, String special) {
		this.username = username;
		this.special = special;
	}

	public String getUsername() {
		return username;
	}

	public String getSpecial() {
		return special;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((special == null) ? 0 : special.hashCode());
		result = prime * result
				+ ((username == null) ? 0 : username.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		SpecialUser other = (SpecialUser) obj;
		if (special == null) {
			if (other.special != null) {
				return false;
			}
		} else if (!special.equals(other.special)) {
			return false;
		}
		if (username == null) {
			if (other.username != null) {
				return false;
			}
		} else if (!username.equals(other.username)) {
			return false;
		}
		return true;
	}
}
