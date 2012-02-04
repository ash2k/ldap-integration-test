package com.ash2k.example.ldap_it.api;

import com.ash2k.example.ldap_it.domain.SpecialUser;

/**
 * Service that can persist and load {@link SpecialUser}s.
 * 
 * @author Mikhail Mazursky
 */
public interface SpecialUserService {
	void persist(SpecialUser user);

	SpecialUser load(String username);
}
