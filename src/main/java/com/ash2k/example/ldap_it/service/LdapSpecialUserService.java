package com.ash2k.example.ldap_it.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapEncoder;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.AbstractContextMapper;
import org.springframework.stereotype.Service;

import com.ash2k.example.ldap_it.api.SpecialUserService;
import com.ash2k.example.ldap_it.domain.SpecialUser;

/**
 * Service that can persist and load {@link SpecialUser}s to/from directory.
 * 
 * @author Mikhail Mazursky
 */
@Service
public final class LdapSpecialUserService implements SpecialUserService {

	public static final String USERDN_FORMAT = "uid=%s,ou=users";

	public static final String LDAP_MAIN_CLASS = "specialUser";
	public static final String LDAP_OBJECT_CLASS = "objectclass";
	public static final String[] LDAP_OBJECT_CLASSES = new String[] {
			LDAP_MAIN_CLASS, "inetOrgPerson", "organizationalPerson", "person",
			"top" };
	public static final String LDAP_USERNAME = "cn";
	public static final String LDAP_SURNAME = "sn";
	public static final String LDAP_SPECIAL = "special";

	private static final ContextMapper mapper = new SpecialUserContextMapper();

	private final LdapTemplate ldapTemplate;

	@Autowired
	public LdapSpecialUserService(LdapTemplate ldapTemplate) {
		this.ldapTemplate = ldapTemplate;
	}

	@Override
	public void persist(SpecialUser user) {
		String userDn = String.format(USERDN_FORMAT,
				LdapEncoder.nameEncode(user.getUsername()));

		DirContextOperations userCtx = new DirContextAdapter(userDn);
		setAttributes(userCtx, user);
		ldapTemplate.bind(userCtx);
	}

	@Override
	public SpecialUser load(String username) {
		String userDn = String.format(USERDN_FORMAT,
				LdapEncoder.nameEncode(username));

		return (SpecialUser) ldapTemplate.lookup(userDn, mapper);
	}

	private void setAttributes(DirContextOperations userCtx, SpecialUser user) {
		userCtx.setAttributeValues(LDAP_OBJECT_CLASS, LDAP_OBJECT_CLASSES);
		userCtx.setAttributeValue(LDAP_USERNAME, user.getUsername());
		userCtx.setAttributeValue(LDAP_SURNAME, user.getUsername());
		userCtx.setAttributeValue(LDAP_SPECIAL, user.getSpecial());
	}

	// package private to make it testable
	static final class SpecialUserContextMapper extends AbstractContextMapper {

		@Override
		protected Object doMapFromContext(DirContextOperations ctx) {
			return new SpecialUser(ctx.getStringAttribute(LDAP_USERNAME),
					ctx.getStringAttribute(LDAP_SPECIAL));
		}
	}
}
