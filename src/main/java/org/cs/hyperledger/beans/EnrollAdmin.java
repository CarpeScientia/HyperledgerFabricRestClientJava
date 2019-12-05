/*
SPDX-License-Identifier: Apache-2.0
 */

package org.cs.hyperledger.beans;

import java.nio.file.Paths;
import java.util.Properties;

import org.hyperledger.fabric.gateway.Wallet;
import org.hyperledger.fabric.gateway.Wallet.Identity;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.security.CryptoSuiteFactory;
import org.hyperledger.fabric_ca.sdk.EnrollmentRequest;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component("enrollAdmin")
public class EnrollAdmin {
	Logger log = LoggerFactory.getLogger(EnrollAdmin.class);

	@Autowired
	public EnrollAdmin( @Value("${hfcaclient.url}") String hfcaClientUrl, 
			@Value("${hfcaclient.CAPemFile}") String hfcaclientCAPemFile, 
			Environment env){
		try {
			// Create a CA client for interacting with the CA.
			Properties props = new Properties();
			props.put("pemFile", hfcaclientCAPemFile);
			props.put("allowAllHostNames", "true");
			HFCAClient caClient = HFCAClient.createNewInstance(hfcaClientUrl, props);
			CryptoSuite cryptoSuite = CryptoSuiteFactory.getDefault().getCryptoSuite();
			caClient.setCryptoSuite(cryptoSuite);

			// Create a wallet for managing identities
			Wallet wallet = Wallet.createFileSystemWallet(Paths.get("wallet"));

			// Check to see if we've already enrolled the admin user.
			boolean adminExists = wallet.exists("admin");
			if (adminExists) {
				log.info("An identity for the admin user \"admin\" already exists in the wallet");
				return;
			}
			//TODO move to secure value store
			String password = env.getProperty("wallet.password");
			// Enroll the admin user, and import the new identity into the wallet.
			final EnrollmentRequest enrollmentRequestTLS = new EnrollmentRequest();
			enrollmentRequestTLS.addHost("localhost");
			enrollmentRequestTLS.setProfile("tls");
			Enrollment enrollment = caClient.enroll("admin", password, enrollmentRequestTLS);
			Identity user = Identity.createIdentity("Org1MSP", enrollment.getCert(), enrollment.getKey());
			wallet.put("admin", user);
			log.info("Successfully enrolled user \"admin\" and imported it into the wallet");
		}catch(Throwable t) {
			log.error("fatal error", t);
			throw new BeanCreationException("EnrollAdmin failed", t);
		}
	}
}
