package neo.sketchware.ai;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.UUID;

/** Durable, privacy-preserving record of an agent task. The app stores this in private files. */
public class AgentRun implements Serializable {
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String CANCELLING = "CANCELLING";
    public static final String CANCELLED = "CANCELLED";

    public String id = UUID.randomUUID().toString();
    public String title = "Agent task";
    public String prompt = "";
    /** Snapshot of reusable context captured when the task was queued. */
    public String reusableContext = "";
    public String status = QUEUED;
    public String currentStage = "Waiting for agent";
    public String plannerAgentId;
    public String builderAgentId;
    public String reviewerAgentId;
    public boolean multiAgent = true;
    public int progress;
    public long createdAt = System.currentTimeMillis();
    public long updatedAt = createdAt;
    public String planOutput = "";
    public String draftOutput = "";
    public String reviewOutput = "";
    public String finalOutput = "";
    public String errorMessage = "";
    public String createdProjectId = "";
    public ArrayList<Event> events = new ArrayList<>();

    public void record(String stage, String message) {
        currentStage = stage == null ? "Working" : stage;
        updatedAt = System.currentTimeMillis();
        if (events == null) events = new ArrayList<>();
        events.add(new Event(updatedAt, currentStage, message == null ? "" : message));
        while (events.size() > 80) events.remove(0);
    }

    public static class Event implements Serializable {
        public long time;
        public String stage;
        public String message;
        public Event() {}
        public Event(long time, String stage, String message) {
            this.time = time;
            this.stage = stage;
            this.message = message;
        }
    }
}
