/*
SPDX-License-Identifier: Apache-2.0
 */

package org.cs.hyperledger.beans;

import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.Properties;
import java.util.Set;

import org.hyperledger.fabric.gateway.Wallet;
import org.hyperledger.fabric.gateway.Wallet.Identity;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.security.CryptoSuiteFactory;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn({"enrollAdmin"})
public class RegisterUser {

	Logger log = LoggerFactory.getLogger(RegisterUser.class);
	
	public RegisterUser( @Value("${hfcaclient.url}") String hfcaClientUrl, 
			@Value("${hfcaclient.CAPemFile}") String hfcaclientCAPemFile)  {
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

			// Check to see if we've already enrolled the user.
			boolean userExists = wallet.exists("user1");
			if (userExists) {
				log.info("An identity for the user \"user1\" already exists in the wallet");
				return;
			}

			userExists = wallet.exists("admin");
			if (!userExists) {
				log.error("\"admin\" needs to be enrolled and added to the wallet first");
				throw new Exception("\"admin\" needs to be enrolled and added to the wallet first");
			}

			Identity adminIdentity = wallet.get("admin");
			User admin = new User() {

				@Override
				public String getName() {
					return "admin";
				}

				@Override
				public Set<String> getRoles() {
					return null;
				}

				@Override
				public String getAccount() {
					return null;
				}

				@Override
				public String getAffiliation() {
					return "org1.department1";
				}

				@Override
				public Enrollment getEnrollment() {
					return new Enrollment() {

						@Override
						public PrivateKey getKey() {
							return adminIdentity.getPrivateKey();
						}

						@Override
						public String getCert() {
							return adminIdentity.getCertificate();
						}
					};
				}

				@Override
				public String getMspId() {
					return "Org1MSP";
				}

			};

			// Register the user, enroll the user, and import the new identity into the wallet.
			RegistrationRequest registrationRequest = new RegistrationRequest("user1");
			registrationRequest.setAffiliation("org1.department1");
			registrationRequest.setEnrollmentID("user1");
			String enrollmentSecret = caClient.register(registrationRequest, admin);
			Enrollment enrollment = caClient.enroll("user1", enrollmentSecret);
			Identity user = Identity.createIdentity("Org1MSP", enrollment.getCert(), enrollment.getKey());
			wallet.put("user1", user);
			log.info("Successfully enrolled user \"user1\" and imported it into the wallet");
		}catch(Throwable t) {
			log.error("fatal error", t);
			throw new BeanCreationException("RegisterUser failed", t);
		}
	}

}
