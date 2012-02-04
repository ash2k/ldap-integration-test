package com.ash2k.example.ldap_it.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.core.partition.ldif.LdifPartition;
import org.apache.directory.server.core.schema.SchemaPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.directory.server.xdbm.Index;
import org.apache.directory.shared.ldap.entry.ServerEntry;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.schema.ldif.extractor.SchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.ldif.extractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.shared.ldap.schema.loader.ldif.LdifSchemaLoader;
import org.apache.directory.shared.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.shared.ldap.schema.registries.SchemaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.Assert;

/**
 * Embedded Apache Directory Server.
 * <p>
 * Some of the code borrowed from [1] and [2]. Some of it i wrote myself.
 * <p>
 * [1]: https://svn.apache.org/repos/asf/directory/documentation
 * /samples/trunk/embedded
 * -sample/src/main/java/org/apache/directory/seserver/EmbeddedADSVer157.java
 * <p>
 * [2]:
 * https://github.com/SpringSource/spring-security/blob/master/ldap/src/main
 * /java/org/springframework/security/ldap/server/ApacheDSContainer.java
 * 
 * @author Mikhail Mazursky
 */
public class EmbeddedADSVer157 implements Lifecycle, ApplicationContextAware {

	private static final Logger logger = LoggerFactory
			.getLogger(EmbeddedADSVer157.class);

	private static final Random r = new Random();

	/** The directory service */
	private DirectoryService service;

	/** The LDAP server */
	private LdapServer server;

	private ApplicationContext ctx;
	private File workingDir;

	private boolean running;
	private String root, ldifResources, schemaResources;
	private int port = 53389;
	private String address;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		ctx = applicationContext;
	}

	public void setWorkingDirectory(File workingDir) {
		Assert.notNull(workingDir);

		logger.info("Setting working directory for LDAP_PROVIDER: {}",
				workingDir.getAbsolutePath());

		if (workingDir.exists()) {
			throw new IllegalArgumentException(
					"The specified working directory '"
							+ workingDir.getAbsolutePath()
							+ "' already exists. Another directory service instance may be using it or it may be from a "
							+ " previous unclean shutdown. Please confirm and delete it or configure a different "
							+ "working directory");
		}

		this.workingDir = workingDir;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getRoot() {
		return root;
	}

	public int getPort() {
		return port;
	}

	public void setRoot(String root) {
		this.root = root;
	}

	public DirectoryService getService() {
		return service;
	}

	public void setLdifResources(String ldifResources) {
		this.ldifResources = ldifResources;
	}

	public void setSchemaResources(String schemaResources) {
		this.schemaResources = schemaResources;
	}

	/**
	 * Add a new partition to the server
	 * 
	 * @param partitionId
	 *            The partition Id
	 * @param partitionDn
	 *            The partition DN
	 * @return The newly added partition
	 * @throws Exception
	 *             If the partition can't be added
	 */
	private Partition addPartition(String partitionId, String partitionDn)
			throws Exception {
		// Create a new partition
		JdbmPartition partition = new JdbmPartition();
		partition.setId(partitionId);
		partition.setPartitionDir(new File(service.getWorkingDirectory(),
				partitionId));
		partition.setSuffix(partitionDn);
		service.addPartition(partition);

		return partition;
	}

	/**
	 * Add a new set of index on the given attributes
	 * 
	 * @param partition
	 *            The partition on which we want to add index
	 * @param attrs
	 *            The list of attributes to index
	 */
	private void addIndex(Partition partition, String... attrs) {
		// Index some attributes on the apache partition
		Set<Index<?, ServerEntry, Long>> indexedAttributes = new HashSet<Index<?, ServerEntry, Long>>();

		for (String attribute : attrs) {
			indexedAttributes
					.add(new JdbmIndex<String, ServerEntry>(attribute));
		}

		((JdbmPartition) partition).setIndexedAttributes(indexedAttributes);
	}

	/**
	 * initialize the schema manager and add the schema partition to diectory
	 * service
	 * 
	 * @throws Exception
	 *             if the schema LDIF files are not found on the classpath
	 */
	private void initSchemaPartition() throws Exception {
		SchemaPartition schemaPartition = service.getSchemaService()
				.getSchemaPartition();

		// Init the LdifPartition
		LdifPartition ldifPartition = new LdifPartition();
		String workingDirectory = service.getWorkingDirectory().getPath();
		ldifPartition.setWorkingDirectory(workingDirectory + "/schema");

		// Extract the schema on disk (a brand new one) and load the registries
		File schemaRepository = new File(workingDirectory, "schema");
		SchemaLdifExtractor extractor = new DefaultSchemaLdifExtractor(
				new File(workingDirectory));
		extractor.extractOrCopy(true);

		schemaPartition.setWrappedPartition(ldifPartition);

		SchemaLoader loader = new LdifSchemaLoader(schemaRepository);
		SchemaManager schemaManager = new DefaultSchemaManager(loader);
		service.setSchemaManager(schemaManager);

		// We have to load the schema now, otherwise we won't be able
		// to initialize the Partitions, as we won't be able to parse
		// and normalize their suffix DN
		schemaManager.loadAllEnabled();

		schemaPartition.setSchemaManager(schemaManager);

		List<Throwable> errors = schemaManager.getErrors();

		if (errors.size() != 0) {
			throw new Exception("Schema load failed : " + errors);
		}
	}

	/**
	 * Initialize the server. It creates the partition, adds the index, and
	 * injects the context entries for the created partitions.
	 * 
	 * @throws Exception
	 *             if there were some problems while initializing the system
	 */
	private void initDirectoryService() throws Exception {
		// Initialize the LDAP service
		service = new DefaultDirectoryService();
		service.setWorkingDirectory(workingDir);

		// first load the schema
		initSchemaPartition();

		// then the system partition
		// this is a MANDATORY partition
		Partition systemPartition = addPartition("system",
				ServerDNConstants.SYSTEM_DN);
		service.setSystemPartition(systemPartition);

		// Disable the ChangeLog system
		service.getChangeLog().setEnabled(false);
		service.setDenormalizeOpAttrsEnabled(true);

		// Now we can create as many partitions as we need
		Partition rootPartition = addPartition("root", root);

		// Index some attributes on the root partition
		addIndex(rootPartition, "objectClass", "ou", "uid", "uniqueMember");

		// And start the service
		service.startup();

		// Inject the root entry
		if (!service.getAdminSession().exists(rootPartition.getSuffixDn())) {
			DN dnRoot = new DN(root);
			ServerEntry entryRoot = service.newEntry(dnRoot);
			entryRoot.add("objectClass", "top", "organizationalUnit",
					"extensibleObject");
			entryRoot.add("dc", "root");
			service.getAdminSession().add(entryRoot);
		}

		// We are all done !
	}

	private void initDirectoryServer() throws Exception {
		server = new LdapServer();
		server.setDirectoryService(service);
		server.setTransports(new TcpTransport(port));

		server.start();
	}

	@PostConstruct
	@Override
	public void start() {
		if (isRunning()) {
			return;
		}
		if (workingDir == null) {
			String apacheWorkDir = System.getProperty("apacheDSWorkDir");

			if (apacheWorkDir == null) {
				apacheWorkDir = System.getProperty("java.io.tmpdir")
						+ File.separatorChar + "apacheds_" + r.nextLong();
			}

			setWorkingDirectory(new File(apacheWorkDir));
		}
		if (service != null && service.isStarted()) {
			throw new IllegalStateException(
					"DirectoryService is already running.");
		}

		logger.info("Starting directory server...");
		try {
			initDirectoryService();
			initDirectoryServer();
		} catch (Exception e) {
			logger.error("Server startup failed ", e);
			return;
		}

		running = true;

		try {
			importSchemas();
		} catch (Exception e) {
			logger.error("Failed to import schema file(s)", e);
		}
		try {
			importLdifs();
		} catch (Exception e) {
			logger.error("Failed to import LDIF file(s)", e);
		}
	}

	@PreDestroy
	@Override
	public void stop() {
		if (!isRunning()) {
			return;
		}

		logger.info("Shutting down directory server ...");
		try {
			server.stop();
			service.shutdown();
		} catch (Exception e) {
			logger.error("Shutdown failed", e);
			return;
		}

		running = false;

		if (workingDir.exists()) {
			logger.info("Deleting working directory {}",
					workingDir.getAbsolutePath());
			deleteDir(workingDir);
		}
	}

	private void importSchemas() throws Exception {
		if (ldifResources == null) {
			return;
		}

		// Import any ldif files
		Resource[] schemas;

		if (ctx == null) {
			// Not running within an app context
			schemas = new PathMatchingResourcePatternResolver()
					.getResources(schemaResources);
		} else {
			schemas = ctx.getResources(schemaResources);
		}
		if (schemas != null) {
			DirContext ctx = null;
			for (Resource schema : schemas) {
				String schemaFile = schema.getFile().getAbsolutePath();
				logger.info("Loading schema file: {}", schemaFile);
				if (ctx == null) {
					ctx = initContext();
				}
				importSchemaFile(ctx, schemaFile);
			}
		}
	}

	private void importSchemaFile(DirContext ctx, String schemaFile)
			throws Exception {

		StringBuilder sb = new StringBuilder();
		BufferedReader isr = new BufferedReader(new InputStreamReader(
				new FileInputStream(schemaFile), Charset.forName("UTF-8")));
		try {
			String line = null;
			while (null != (line = isr.readLine())) {
				line = line.trim();
				if (line.isEmpty()) {
					// Separator line - we finished loading a schema entry
					String schemaAtr = sb.toString().trim();
					if (schemaAtr.isEmpty()) {
						// Several empty lines after previous schema entry
						continue;
					}
					sb = new StringBuilder();
					addSchemaAttribute(ctx, schemaAtr);
				} else if (line.startsWith("#")) {
					// Comment
				} else {
					sb.append(line).append(' ');
				}
			}
		} finally {
			isr.close();
		}
		String schemaAtr = sb.toString().trim();
		if (schemaAtr.isEmpty()) {
			// Several empty lines after previous schema entry
			return;
		}
		addSchemaAttribute(ctx, schemaAtr);
	}

	private boolean addSchemaAttribute(DirContext ctx, String schemaAtr)
			throws NamingException {

		Attributes atAttrs = new BasicAttributes(true);
		if (schemaAtr.toLowerCase().startsWith("attributetype")) {
			atAttrs.put("attributeTypes",
					schemaAtr.substring("attributetype".length()));
		} else if (schemaAtr.toLowerCase().startsWith("objectclass")) {
			atAttrs.put("objectClasses",
					schemaAtr.substring("objectclass".length()));
		} else {
			logger.warn("Unknown schema entry format; skipped: {}", schemaAtr);
			return false;
		}
		ctx.modifyAttributes("cn=schema", DirContext.ADD_ATTRIBUTE, atAttrs);
		return true;
	}

	private DirContext initContext() throws Exception {
		Hashtable<Object, Object> env = new Hashtable<Object, Object>();
		env.put(Context.INITIAL_CONTEXT_FACTORY,
				"com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, "ldap://127.0.0.1:" + port + "/");
		env.put(Context.SECURITY_PRINCIPAL, "uid=admin,ou=system");
		env.put(Context.SECURITY_CREDENTIALS, "secret");

		return new InitialDirContext(env);
	}

	private void importLdifs() throws Exception {
		if (ldifResources == null) {
			return;
		}

		// Import any ldif files
		Resource[] ldifs;

		if (ctx == null) {
			// Not running within an app context
			ldifs = new PathMatchingResourcePatternResolver()
					.getResources(ldifResources);
		} else {
			ldifs = ctx.getResources(ldifResources);
		}

		// Note that we can't just import using the ServerContext returned
		// from starting Apace DS, apparently because of the long-running issue
		// DIRSERVER-169.
		// We need a standard context.
		// DirContext dirContext = contextSource.getReadWriteContext();

		if (ldifs != null) {
			for (Resource ldif : ldifs) {
				String ldifFile = ldif.getFile().getAbsolutePath();
				logger.info("Loading LDIF file: {}", ldifFile);
				LdifFileLoader loader = new LdifFileLoader(
						service.getAdminSession(), ldifFile);
				loader.execute();
			}
		}
	}

	private boolean deleteDir(File dir) {
		if (dir.isDirectory()) {
			for (String child : dir.list()) {
				if (!deleteDir(new File(dir, child))) {
					return false;
				}
			}
		}

		return dir.delete();
	}

	@Override
	public boolean isRunning() {
		return running;
	}
}
