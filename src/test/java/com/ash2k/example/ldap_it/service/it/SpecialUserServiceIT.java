package com.ash2k.example.ldap_it.service.it;

import static org.testng.Assert.assertEquals;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import com.ash2k.example.ldap_it.api.SpecialUserService;
import com.ash2k.example.ldap_it.domain.SpecialUser;
import com.ash2k.example.ldap_it.service.LdapSpecialUserService;

/**
 * Integration test for {@link LdapSpecialUserService} class.
 * 
 * @author Mikhail Mazursky
 */
@ContextConfiguration(classes = { Config.class })
@Test(singleThreaded = true)
public class SpecialUserServiceIT extends AbstractTestNGSpringContextTests {
	static final String USERNAME = "user1";
	static final String SPECIAL = "user1_special";

	@Autowired
	SpecialUserService service;

	@Test
	public void loadShouldFindPersistedUser() {
		// arrange
		SpecialUser user = new SpecialUser(USERNAME, SPECIAL);
		service.persist(user);

		// act
		SpecialUser loadedUser = service.load(user.getUsername());

		// assert
		assertEquals(loadedUser, user);
	}
}
