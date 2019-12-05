package org.cs.hyperledger.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.Network;
import org.hyperledger.fabric.gateway.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BasicRestController {
	Logger log = LoggerFactory.getLogger(BasicRestController.class);

	@Value("${gateway.networkConfigFile}")
	private String networkConfigFile;

	@Value("${gateway.networkChannel}")
	private String networkChannel;

	@Value("${gateway.networkContract}")
	private String networkContract;

	@PostMapping("/contract/{operation}/{arguments}")
	public String doForContract(
			@PathVariable String operation,
			@PathVariable String[] arguments
			) {
		return callContract(operation, arguments, true);
	}
	@GetMapping("/contract/{operation}/{arguments}")
	public String getFromContract(
			@PathVariable String operation,
			@PathVariable String[] arguments
			) {
		return callContract(operation, arguments, false);
	}

	private String callContract(String operation, String[] arguments, boolean isPost) {
		Gateway.Builder builder;
		try {//no word on thread safety of this stuff
			// Load a file system based wallet for managing identities.
			Path walletPath = Paths.get("wallet");
			Wallet wallet = Wallet.createFileSystemWallet(walletPath);
			// load a CCP
			Path networkConfigPath = Paths.get(networkConfigFile);

			builder = Gateway.createBuilder();
			builder.identity(wallet, "user1").networkConfig(networkConfigPath).discovery(true);
		}catch(Exception e) {
			log.error("Gateway setup failed", e);
			return "Gateway setup failed";
		}
		// create a gateway connection
		try (Gateway gateway = builder.connect()) {
			// get the network and contract
			Network network = gateway.getNetwork(networkChannel);
			Contract contract = network.getContract(networkContract);

			byte[] result;
			if(isPost) {//goes to orderer
				result = contract.submitTransaction(operation,arguments);//fails with GatewayRuntimeException if tx rejected, no contract message is shown. bad design
			}else {
				result = contract.evaluateTransaction(operation,arguments);
			}
			if(log.isDebugEnabled()) log.debug("Result of "+ operation +": " + new String(result));
			return new String(result, StandardCharsets.UTF_8);

		}catch(Exception e) {
			log.error("Contract operation failed " + operation);
			return "operation failed";
		}
	}

}