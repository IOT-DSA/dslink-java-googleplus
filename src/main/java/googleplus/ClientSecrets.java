package googleplus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientSecrets implements Serializable {
	private static final Logger LOGGER;
	
	static {
		LOGGER = LoggerFactory.getLogger(ClientSecrets.class);
	}
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -1535342087639690498L;
	private String clientID;
	private String clientSecret;
	
	ClientSecrets(String id, String secret) {
		clientID = id;
		clientSecret = secret;
	}
	
	String getID() {
		return clientID;
	}
	
	String getSecret() {
		return clientSecret;
	}
	
	void save(File file) {
		try {
            FileOutputStream fileOut = new FileOutputStream(file);
            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
            objectOut.writeObject(this);
            objectOut.close();
        } catch (IOException e) {
            String msg = "IOException while saving clientSecrets.";
            LOGGER.error(msg);
        }
	}
	
	static ClientSecrets load(File file) {
		ClientSecrets retval = null;
		if (file.exists()) {
            try {
                FileInputStream fileIn = new FileInputStream(file);
                ObjectInputStream objectIn = new ObjectInputStream(fileIn);
                retval = (ClientSecrets) objectIn.readObject();
                objectIn.close();
            } catch (IOException e) {
                String msg = "IOException while loading accessToken.";
                LOGGER.error(msg);
            } catch (ClassNotFoundException e) {
                String msg = "ClassNotFoundException while loading accessToken.";
                LOGGER.error(msg);
            }
        }
		return retval;
	}
	
	
}
