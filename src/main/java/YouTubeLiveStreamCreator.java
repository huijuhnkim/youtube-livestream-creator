import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;

public class YouTubeLiveStreamCreator {
  private static final String APPLICATION_NAME = "YouTube LiveStream Creator";
  private static final String CREDENTIALS_FOLDER = "credentials";
  private static final java.io.File DATA_STORE_DIR = new java.io.File(CREDENTIALS_FOLDER);
  private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  private static final List<String> SCOPES = Arrays.asList("https://www.googleapis.com/auth/youtube.force-ssl");

  private final YouTube youtube;

  public YouTubeLiveStreamCreator() throws Exception {
    // Create credentials folder if it doesn't exist
    if (!DATA_STORE_DIR.exists()) {
      DATA_STORE_DIR.mkdirs();
    }

    // Load client secrets from resources
    InputStream in = getClass().getResourceAsStream("/client_secret.json");
    if (in == null) {
      // If not found in resources, try to load from current directory
      String currentDir = System.getProperty("user.dir");
      System.out.println("Looking for client_secret.json in: " + currentDir);
      try {
        in = Files.newInputStream(Paths.get("client_secret.json"));
      } catch (Exception e) {
        throw new Exception("client_secret.json not found! Please ensure it's either:\n" +
                "1. In src/main/resources folder\n" +
                "2. In the current directory: " + currentDir);
      }
    }

    // Load client secrets
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

    // Build authorization flow
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            JSON_FACTORY,
            clientSecrets,
            SCOPES)
            .setDataStoreFactory(new FileDataStoreFactory(DATA_STORE_DIR))
            .setAccessType("offline")
            .build();

    // Authorize and get credentials
    Credential credential = new AuthorizationCodeInstalledApp(
            flow, new LocalServerReceiver())
            .authorize("user");

    System.out.println("Authorization successful!");

    // Initialize YouTube service
    youtube = new YouTube.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            JSON_FACTORY,
            credential)
            .setApplicationName(APPLICATION_NAME)
            .build();
  }

  public String createLiveStream(String title, String description, String scheduledStartTime) throws Exception {
    // Create LiveStream
    LiveStream liveStream = new LiveStream();
    LiveStreamSnippet streamSnippet = new LiveStreamSnippet();
    streamSnippet.setTitle(title);
    liveStream.setSnippet(streamSnippet);

    LiveStreamStatus streamStatus = new LiveStreamStatus();
    streamStatus.setStreamStatus("active");
    liveStream.setStatus(streamStatus);

    LiveStream createdStream = youtube.liveStreams()
            .insert("snippet,status", liveStream)
            .execute();

    // Create LiveBroadcast
    LiveBroadcast broadcast = new LiveBroadcast();
    LiveBroadcastSnippet broadcastSnippet = new LiveBroadcastSnippet();
    broadcastSnippet.setTitle(title);
    broadcastSnippet.setDescription(description);
    broadcastSnippet.setScheduledStartTime(DateTime.parseRfc3339(scheduledStartTime));
    broadcast.setSnippet(broadcastSnippet);

    LiveBroadcastStatus broadcastStatus = new LiveBroadcastStatus();
    broadcastStatus.setPrivacyStatus("private"); // or "public", "unlisted"
    broadcast.setStatus(broadcastStatus);

    LiveBroadcast createdBroadcast = youtube.liveBroadcasts()
            .insert("snippet,status", broadcast)
            .execute();

    // Bind broadcast to stream
    youtube.liveBroadcasts()
            .bind(createdBroadcast.getId(), "id,contentDetails")
            .setStreamId(createdStream.getId())
            .execute();

    return String.format("Stream created successfully!\nStream ID: %s\nBroadcast ID: %s\nStream Key: %s",
            createdStream.getId(),
            createdBroadcast.getId(),
            createdStream.getCdn().getIngestionInfo().getStreamName());
  }

  public static void main(String[] args) {
    try {
      System.out.println("Starting YouTube LiveStream Creator...");
      System.out.println("A browser window should open for authorization. Please follow the prompts.");

      YouTubeLiveStreamCreator creator = new YouTubeLiveStreamCreator();
      String result = creator.createLiveStream(
              "My Test Stream",
              "This is a test live stream created via API",
              "2024-11-15T15:00:00.000Z"
      );
      System.out.println(result);
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}