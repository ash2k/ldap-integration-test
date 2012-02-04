package com.ash2k.example.ldap_it.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ash2k.example.ldap_it.domain.SpecialUser;
import com.ash2k.example.ldap_it.service.LdapSpecialUserService.SpecialUserContextMapper;

/**
 * Unit test for {@link LdapSpecialUserService} and
 * {@link SpecialUserContextMapper}.
 * 
 * @author Mikhail Mazursky
 */
public class LdapSpecialUserServiceTest {
	static final String USERNAME = "user1";
	static final String SPECIAL = "user1_special";

	@Mock
	DirContextOperations ctx;
	@Mock
	LdapTemplate ldapTemplate;
	@Captor
	ArgumentCaptor<DirContextOperations> ctxCaptor;

	LdapSpecialUserService service;

	@BeforeMethod
	public void beforeMethod() {
		MockitoAnnotations.initMocks(this);
		service = new LdapSpecialUserService(ldapTemplate);
	}

	@Test
	public void persistShouldPersistUser() {
		// arrange
		SpecialUser user = new SpecialUser(USERNAME, SPECIAL);

		// act
		service.persist(user);

		// assert
		verify(ldapTemplate).bind(ctxCaptor.capture());
		DirContextOperations userCtx = ctxCaptor.getValue();
		assertEquals(
				userCtx.getStringAttributes(LdapSpecialUserService.LDAP_OBJECT_CLASS),
				LdapSpecialUserService.LDAP_OBJECT_CLASSES);
		assertEquals(
				userCtx.getStringAttribute(LdapSpecialUserService.LDAP_USERNAME),
				user.getUsername());
		assertEquals(
				userCtx.getStringAttribute(LdapSpecialUserService.LDAP_SURNAME),
				user.getUsername());
		assertEquals(
				userCtx.getStringAttribute(LdapSpecialUserService.LDAP_SPECIAL),
				user.getSpecial());
	}

	@Test
	public void doMapFromContextShouldProduceCorrectUser() {
		// arrange
		SpecialUserContextMapper mapper = new SpecialUserContextMapper();
		when(ctx.getStringAttribute(LdapSpecialUserService.LDAP_USERNAME))
				.thenReturn(USERNAME);
		when(ctx.getStringAttribute(LdapSpecialUserService.LDAP_SPECIAL))
				.thenReturn(SPECIAL);

		// act
		Object mapped = mapper.doMapFromContext(ctx);

		// assert
		assertTrue(mapped instanceof SpecialUser);
		SpecialUser user = (SpecialUser) mapped;
		assertEquals(user.getUsername(), USERNAME);
		assertEquals(user.getSpecial(), SPECIAL);
	}
}
