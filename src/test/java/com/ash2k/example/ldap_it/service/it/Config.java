package com.ash2k.example.ldap_it.service.it;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.pool.factory.PoolingContextSource;

import com.ash2k.example.ldap_it.utils.EmbeddedADSVer157;

/**
 * Java-based configuration of integration test execution context.
 * 
 * @author Mikhail Mazursky
 */
@Configuration
@PropertySource("classpath:example.properties")
@ComponentScan("com.ash2k.example.ldap_it.service")
public class Config {

	public static final String PROP_LDAP_HOST = "ldap.host";
	public static final String PROP_LDAP_PORT = "ldap.port";
	public static final String PROP_LDAP_BASE = "ldap.base";
	public static final String PROP_LDAP_USERDN = "ldap.userDn";
	public static final String PROP_LDAP_PASSWORD = "ldap.password";

	@Autowired
	private Environment env;

	@Bean
	public EmbeddedADSVer157 embeddedADS() {
		EmbeddedADSVer157 ads = new EmbeddedADSVer157();

		ads.setAddress(env.getProperty(PROP_LDAP_HOST));
		ads.setPort(Integer.parseInt(env.getProperty(PROP_LDAP_PORT)));

		ads.setRoot(env.getProperty(PROP_LDAP_BASE));
		ads.setLdifResources("classpath:example.ldif");
		ads.setSchemaResources("classpath:example.schema");

		return ads;
	}

	@Bean
	public ContextSource contextSource() {
		LdapContextSource lcs = new LdapContextSource();

		String ldapUrl = String.format("ldap://%s:%d",
				env.getProperty(PROP_LDAP_HOST),
				Integer.parseInt(env.getProperty(PROP_LDAP_PORT)));

		lcs.setUrl(ldapUrl);
		lcs.setBase(env.getProperty(PROP_LDAP_BASE));
		lcs.setUserDn(env.getProperty(PROP_LDAP_USERDN));
		lcs.setPassword(env.getProperty(PROP_LDAP_PASSWORD));

		return lcs;
	}

	@Bean
	public ContextSource poolingContextSource() {
		PoolingContextSource cs = new PoolingContextSource();
		cs.setContextSource(contextSource());
		return cs;
	}

	@Bean
	public LdapTemplate ldapTemplate() {
		return new LdapTemplate(poolingContextSource());
	}
}
