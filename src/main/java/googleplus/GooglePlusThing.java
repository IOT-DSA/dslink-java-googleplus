package googleplus;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeBuilder;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Handler;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.Plus.Activities;
import com.google.api.services.plus.Plus.People;
import com.google.api.services.plus.PlusScopes;
import com.google.api.services.plus.model.ItemScope;
import com.google.api.services.plus.model.Moment;


public class GooglePlusThing {
	private static final Logger LOGGER;
	
	static {
		LOGGER = LoggerFactory.getLogger(GooglePlusThing.class);
	}
	
	private Node node;
	private Node err;
	private String username;
	private ClientSecrets client;
	private final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private Plus plus;
	private GoogleAuthorizationCodeFlow flow;
	private NetHttpTransport httpTransport;
	private static final String APPLICATION_NAME = "DGLogikBot";
	private static final java.io.File DATA_STORE_DIR =
		      new java.io.File(System.getProperty("user.home"), "dgplusbot");

	
	private GooglePlusThing(Node node) {
		this.node = node;
	}
	
	public static void start(Node parent) {
		Node node = parent.createChild("googlePlus").build();
		final GooglePlusThing gp = new GooglePlusThing(node);
		gp.init();
	}
	
	private void clearErrorMsgs() {
		if (err.getChildren() == null) return;
		for (Node child: err.getChildren().values()) {
			err.removeChild(child);
		}
	}
	
	private void init() {
		
		err = node.createChild("errors").build();
		
		NodeBuilder builder = node.createChild("resetEverything");
		builder.setAction(new Action(Permission.READ, new ResetHandler()));
		builder.build();
		
		if (client == null) client = ClientSecrets.load(new File(DATA_STORE_DIR, "clientSecrets.ser"));
		if (client == null) {
			makeSetupAction();
			return;
		}
		String clientID = client.getID();
		String clientSecret = client.getSecret();
		
		try {
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
			FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
		    
		    flow = new GoogleAuthorizationCodeFlow.Builder(
		            httpTransport, JSON_FACTORY, clientID, clientSecret,
		            Collections.singleton(PlusScopes.PLUS_LOGIN)).setDataStoreFactory(
		            dataStoreFactory).build();
		    
		    client.save(new File(DATA_STORE_DIR, "clientSecrets.ser"));
		    //Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
		    //plus = new Plus.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
		    
		} catch (GeneralSecurityException e) {
			resetEverything();
			return;
			//e.printStackTrace();
		} catch (IOException e) {
			if (e instanceof HttpResponseException) {
				resetEverything();
				builder = err.createChild("auth error message");
				builder.setValue(new Value("Error with OAuth, reseting"));
				builder.build();
				return;
			}
			LOGGER.debug("error: ", e);
		}
	    
		Action act = new Action(Permission.READ, new LoginHandler());
		act.addParameter(new Parameter("username", ValueType.STRING));
		node.createChild("login").setAction(act).build();
	}
	
	private void makeSetupAction() {
		Action act = new Action(Permission.READ, new SetupHandler());
		act.addParameter(new Parameter("clientID", ValueType.STRING));
		act.addParameter(new Parameter("clientSecret", ValueType.STRING));
		node.createChild("setupClientSecrets").setAction(act).build();
	}
	
	private void connect() {
		clearErrorMsgs();
		Action act = new Action(Permission.READ, new ActivitySearchHandler());
		act.addParameter(new Parameter("query", ValueType.STRING));
		node.createChild("searchActivities").setAction(act).build();
		act = new Action(Permission.READ, new PeopleSearchHandler());
		act.addParameter(new Parameter("query", ValueType.STRING));
		node.createChild("searchPeople").setAction(act).build();
		act = new Action(Permission.READ, new MomentHandler());
		act.addParameter(new Parameter("name", ValueType.STRING));
		act.addParameter(new Parameter("description", ValueType.STRING));
		node.createChild("makeMoment").setAction(act).build();
	}
	
	private class SetupHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			String id = event.getParameter("clientID", ValueType.STRING).getString();
			String secret = event.getParameter("clientSecret", ValueType.STRING).getString();
			client = new ClientSecrets(id, secret);
			node.removeChild("setupClientSecrets");
			init();
			
		}
	}
	
	private class LoginHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			username = event.getParameter("username", ValueType.STRING).getString();
			
			NodeBuilder builder = node.createChild("logout");
			builder.setAction(new Action(Permission.READ, new LogoutHandler()));
			builder.build();
			builder = node.createChild("deleteUserAccount");
			builder.setAction(new Action(Permission.READ, new AccountDeleteHandler()));
			builder.build();
			
			node.removeChild("login");
			
			try {
				Credential credential = flow.loadCredential(username);
				if (credential != null) {
					plus = new Plus.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
					connect();
					return;
				}
				
				String authurl = flow.newAuthorizationUrl().set("requestvisibleactions", "http://schema.org/AddAction").setRedirectUri("http://localhost").build();
				builder = node.createChild("Authorization URL");
				builder.setValue(new Value(authurl));
				builder.build();
				Action act = new Action(Permission.READ, new AuthHandler());
				act.addParameter(new Parameter("redirectUrl", ValueType.STRING));
				node.createChild("Authorize").setAction(act).build();
				
			} catch (IOException e) {
				if (e instanceof HttpResponseException) {
					resetEverything();
					builder = err.createChild("auth error message");
					builder.setValue(new Value("Error with OAuth, reseting"));
					builder.build();
					return;
				}
				LOGGER.debug("error: ", e);
			}
		}
	}
	
	private class AuthHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			String redurl = event.getParameter("redirectUrl", ValueType.STRING).getString();
			String code = redurl.split("code=")[1].split("[&;]")[0];
			try {
				TokenResponse tr = flow.newTokenRequest(code).setRedirectUri("http://localhost").execute();
				Credential credential = flow.createAndStoreCredential(tr, username);
				plus = new Plus.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
				connect();
				node.removeChild("Authorize");
			} catch (IOException e) {
				if (e instanceof HttpResponseException) {
					resetEverything();
					NodeBuilder builder = err.createChild("auth error message");
					builder.setValue(new Value("Error with OAuth, reseting"));
					builder.build();
					return;
				}
				LOGGER.debug("error: ", e);
			}
		}
	}
	
	private class ActivitySearchHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			String query = event.getParameter("query", ValueType.STRING).getString();
			Activities acts = plus.activities();
			String result = null;
			try {
				result = acts.search(query).execute().toString();
			} catch (IOException e) {
				if (e instanceof HttpResponseException) {
					resetEverything();
					NodeBuilder builder = err.createChild("auth error message");
					builder.setValue(new Value("Error with OAuth, reseting"));
					builder.build();
					return;
				}
				LOGGER.debug("error: ", e);
			}
			Node sr = node.getChild("SearchResults");
			if (sr == null) {
				NodeBuilder builder = node.createChild("SearchResults");
				builder.setValue(new Value(result));
				builder.build();
			} else {
				sr.setValue(new Value(result));
			}
		}
	}
	
	private class PeopleSearchHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			String query = event.getParameter("query", ValueType.STRING).getString();
			People ppl = plus.people();
			String result = null;
			try {
				result = ppl.search(query).execute().toString();
			} catch (IOException e) {
				if (e instanceof HttpResponseException) {
					resetEverything();
					NodeBuilder builder = err.createChild("auth error message");
					builder.setValue(new Value("Error with OAuth, reseting"));
					builder.build();
					return;
				}
				LOGGER.debug("error: ", e);
			}
			Node sr = node.getChild("SearchResults");
			if (sr == null) {
				NodeBuilder builder = node.createChild("SearchResults");
				builder.setValue(new Value(result));
				builder.build();
			} else {
				sr.setValue(new Value(result));
			}
		}
	}
	
	private class MomentHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			String name = event.getParameter("name", ValueType.STRING).getString();
			String description = event.getParameter("description", ValueType.STRING).getString();
			Moment moment = new Moment();
			moment.setType("http://schema.org/AddAction");
			ItemScope itemScope = new ItemScope();
			itemScope.setId("target-id-1");
			itemScope.setType("http://schema.org/AddAction");
			itemScope.setName(name);
			itemScope.setDescription(description);
			moment.setObject(itemScope);
			try {
				Moment result = plus.moments().insert("me", "vault", moment).execute();
				LOGGER.debug(result.getId());
			} catch (IOException e) {
				if (e instanceof HttpResponseException) {
					LOGGER.debug("error: ", e);
					resetEverything();
					NodeBuilder builder = err.createChild("auth error message");
					builder.setValue(new Value("Error with OAuth, reseting"));
					builder.build();
					return;
				}
				LOGGER.debug("error: ", e);
			}
		}
	}
	
	private class LogoutHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			logout();
		}
	}
	
	private void logout() {
		plus = null;
		username = null;
		flow = null;
		httpTransport = null;
		if (node.getChildren() != null) {
			for (Node child: node.getChildren().values()) {
				node.removeChild(child);
			}
		}
		init();
	}
	
	private class AccountDeleteHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			accountDelete();
		}
	}
	
	private void accountDelete() {
		try {
			flow.getCredentialDataStore().delete(username);
			logout();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("error: ", e);
		}
	}
	
	private class ResetHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			resetEverything();
		}
	}
	
	private void resetEverything() {
		client = null;
		for (File f: DATA_STORE_DIR.listFiles()) {
			if (!f.delete()) {
				logout();
				NodeBuilder builder = err.createChild("reset error message");
				builder.setValue(new Value("reset failed. logging out."));
				builder.build();
				return;
			}
		}
		logout();
	}
			
}
