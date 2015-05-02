package googleplus;

import java.io.IOException;
import java.io.InputStreamReader;
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
import org.vertx.java.core.Handler;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.Plus.Activities;
import com.google.api.services.plus.Plus.People;
import com.google.api.services.plus.PlusScopes;

public class GooglePlusThing {
	
	private Node node;
	private String username;
	private final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private Plus plus;
	private GoogleAuthorizationCodeFlow flow;
	private NetHttpTransport httpTransport;
	private static final String APPLICATION_NAME = "DGLogikBot";
	private static final java.io.File DATA_STORE_DIR =
		      new java.io.File(System.getProperty("user.home"), ".store/dgplusbot");

	
	private GooglePlusThing(Node node) {
		this.node = node;
	}
	
	public static void start(Node parent) {
		Node node = parent.createChild("googlePlus").build();
		final GooglePlusThing gp = new GooglePlusThing(node);
		gp.init();
	}
	
	private void init() {
		try {
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
			FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
		    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
		            new InputStreamReader(GooglePlusThing.class.getResourceAsStream("/client_secret.json"), "US-ASCII"));
		    flow = new GoogleAuthorizationCodeFlow.Builder(
		            httpTransport, JSON_FACTORY, clientSecrets,
		            Collections.singleton(PlusScopes.PLUS_ME)).setDataStoreFactory(
		            dataStoreFactory).build();
		    //Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
		    //plus = new Plus.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
		    
		} catch (GeneralSecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
		Action act = new Action(Permission.READ, new LoginHandler());
		act.addParameter(new Parameter("username", ValueType.STRING));
		node.createChild("login").setAction(act).build();
	}
	
	private void connect() {
		Action act = new Action(Permission.READ, new ActivitySearchHandler());
		act.addParameter(new Parameter("query", ValueType.STRING));
		node.createChild("searchActivities").setAction(act).build();
		act = new Action(Permission.READ, new PeopleSearchHandler());
		act.addParameter(new Parameter("query", ValueType.STRING));
		node.createChild("searchPeople").setAction(act).build();
	}
	
	private class LoginHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			username = event.getParameter("username", ValueType.STRING).getString();
			System.out.println(username);
			
			NodeBuilder builder = node.createChild("logout");
			builder.setAction(new Action(Permission.READ, new LogoutHandler()));
			builder.build();
			
			try {
				Credential credential = flow.loadCredential(username);
				if (credential != null) {
					plus = new Plus.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
					connect();
					return;
				}
				
				String authurl = flow.newAuthorizationUrl().setRedirectUri("http://localhost").build();
				builder = node.createChild("Authorization URL");
				builder.setValue(new Value(authurl));
				builder.build();
				Action act = new Action(Permission.READ, new AuthHandler());
				act.addParameter(new Parameter("redirectUrl", ValueType.STRING));
				node.createChild("Authorize").setAction(act).build();
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
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
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			NodeBuilder builder = node.createChild("SearchResults");
			builder.setValue(new Value(result));
			builder.build();
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			NodeBuilder builder = node.createChild("SearchResults");
			builder.setValue(new Value(result));
			builder.build();
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
			
}
