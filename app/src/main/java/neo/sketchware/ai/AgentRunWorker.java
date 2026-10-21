package neo.sketchware.ai;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import pro.sketchware.R;

/**
 * Executes a persistent Planner -> Builder -> Reviewer task outside the UI lifecycle.
 * The worker records only concise stage summaries and final deliverables, not private chain-of-thought.
 */
public class AgentRunWorker extends Worker {
    private static final long REQUEST_TIMEOUT_SECONDS = 115L;
    private static final String NOTIFICATION_CHANNEL_ID = "neo_ai_agent_runs";
    private final Context appContext;

    public AgentRunWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Result doWork() {
        String runId = getInputData().getString("run_id");
        if (TextUtils.isEmpty(runId)) return Result.failure();
        AgentRun run = AgentRunStore.get(appContext, runId);
        if (run == null) return Result.failure();
        if (AgentRun.COMPLETED.equals(run.status)) return Result.success();
        if (isStopped() || AgentRun.CANCELLED.equals(run.status)) return Result.failure();

        // WorkManager may restart a worker after process death. Keep durable stage outputs and
        // resume from the last persisted stage instead of unnecessarily repeating successful calls.
        run.status = AgentRun.RUNNING;
        run.progress = Math.max(1, run.progress);
        run.errorMessage = "";
        run.record("Preparing", "Loading saved task and agent configuration.");
        persist(run);

        try {
            if (TextUtils.isEmpty(run.prompt) || run.prompt.trim().isEmpty()) {
                return fail(run, "The task description is empty. Open the workspace and submit a task.");
            }
            if (run.prompt.length() > 24000) {
                return fail(run, "The task is too long. Keep the prompt under 24,000 characters.");
            }

            List<AiModelConfig> configs = AiManager.getConfigs(appContext);
            AiModelConfig fallback = AiManager.getActiveConfig(appContext);
            if (fallback == null && configs != null && !configs.isEmpty()) fallback = configs.get(0);
            AiModelConfig planner = findAgent(configs, run.plannerAgentId, fallback);
            AiModelConfig builder = findAgent(configs, run.builderAgentId, planner != null ? planner : fallback);
            AiModelConfig reviewer = findAgent(configs, run.reviewerAgentId, builder != null ? builder : fallback);
            if (planner == null || AiProviderRegistry.get(planner.providerId) == null) {
                return fail(run, "No valid AI agent is configured. Add an agent and test its connection first.");
            }
            if (builder == null || AiProviderRegistry.get(builder.providerId) == null) builder = planner;
            if (reviewer == null || AiProviderRegistry.get(reviewer.providerId) == null) reviewer = builder;

            if (!run.multiAgent) {
                run.record("Working", "" + planner.displayName + " is handling the task.");
                run.progress = 12;
                persist(run);
                String answer = request(planner,
                        "You are an experienced Sketchware Neo project agent. Produce a useful, actionable deliverable. " +
                                "State assumptions and verification limits explicitly. Do not reveal private chain-of-thought; summarize decisions only.",
                        taskEnvelope(run.prompt, "Handle the task directly. Organize the response with a concise summary, concrete steps, and relevant code or project artifacts."));
                if (answer == null) return stoppedOrFailed(run, "The agent did not return a response.");
                run.finalOutput = answer;
                run.progress = 100;
                run.status = AgentRun.COMPLETED;
                run.record("Completed", "Single-agent task completed.");
                persist(run);
                return Result.success();
            }

            String plan = run.planOutput == null ? "" : run.planOutput.trim();
            if (plan.isEmpty()) {
                run.record("Plan", planner.displayName + " is preparing the implementation plan.");
                run.progress = Math.max(run.progress, 10);
                persist(run);
                plan = request(planner,
                        "You are the Planner agent in a Sketchware Neo software-building team. " +
                                "Return a compact implementation plan with: goal, constraints, ordered milestones, files/systems likely involved, validation checklist, and unresolved assumptions. " +
                                "Do not pretend to inspect files that were not supplied. Do not reveal private chain-of-thought; provide concise conclusions only.",
                        taskEnvelope(run.prompt, "Produce the plan only. Make it actionable and keep it reasonably concise."));
                if (plan == null) return stoppedOrFailed(run, "Planner did not return a response.");
                run.planOutput = plan;
                run.progress = Math.max(run.progress, 35);
                run.record("Plan ready", "Planner returned a task plan.");
                persist(run);
            } else {
                run.progress = Math.max(run.progress, 35);
                run.record("Plan restored", "Reusing the saved planner output after a background restart.");
                persist(run);
            }

            if (isStopped()) return cancel(run);
            String draft = run.draftOutput == null ? "" : run.draftOutput.trim();
            if (draft.isEmpty()) {
                run.record("Build", builder.displayName + " is creating the deliverable from the plan.");
                run.progress = Math.max(run.progress, 42);
                persist(run);
                draft = request(builder,
                        "You are the Builder agent in a multi-agent Sketchware Neo workflow. Follow the user's task and planner brief. " +
                                "Produce the best complete deliverable you can: concrete implementation/code/specification, integration notes, and test checklist. " +
                                "Keep code in fenced blocks where useful. Call out anything that genuinely requires unavailable tools. " +
                                "Do not reveal private chain-of-thought; show results and brief rationale only.",
                        taskEnvelope(run.prompt, "Planner brief:\n" + plan + "\n\nNow produce the deliverable. Avoid merely repeating the plan."));
                if (draft == null) return stoppedOrFailed(run, "Builder did not return a response.");
                run.draftOutput = draft;
                run.progress = Math.max(run.progress, 72);
                run.record("Draft ready", "Builder returned a draft deliverable.");
                persist(run);
            } else {
                run.progress = Math.max(run.progress, 72);
                run.record("Draft restored", "Reusing the saved builder output after a background restart.");
                persist(run);
            }

            if (isStopped()) return cancel(run);
            String review = run.reviewOutput == null ? "" : run.reviewOutput.trim();
            if (review.isEmpty()) {
                run.record("Review", reviewer.displayName + " is checking the draft for gaps and contradictions.");
                run.progress = Math.max(run.progress, 78);
                persist(run);
                review = request(reviewer,
                        "You are the Reviewer agent for a software task in Sketchware Neo. Verify the draft against the user's request and planner brief. " +
                                "Look for missing requirements, likely build/runtime risks, unsafe assumptions, broken APIs, and unsupported claims. " +
                                "Return a corrected final deliverable rather than hidden chain-of-thought. If something cannot be verified, label it clearly.",
                        taskEnvelope(run.prompt, "Planner brief:\n" + plan + "\n\nBuilder draft:\n" + draft +
                                "\n\nReview it and return the corrected final answer with sections: Final deliverable, Validation checklist, Known limitations."));
                if (review == null) return stoppedOrFailed(run, "Reviewer did not return a response.");
                run.reviewOutput = review;
                persist(run);
            } else {
                run.record("Review restored", "Reusing the saved reviewer output after a background restart.");
                persist(run);
            }
            run.finalOutput = review;
            run.progress = 100;
            run.status = AgentRun.COMPLETED;
            run.record("Completed", "Planner, Builder and Reviewer stages completed.");
            persist(run);
            return Result.success();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return cancel(run);
        } catch (Exception e) {
            String message = e.getMessage();
            return fail(run, TextUtils.isEmpty(message) ? "Agent workflow failed unexpectedly." : message);
        }
    }

    private AiModelConfig findAgent(List<AiModelConfig> configs, String id, AiModelConfig fallback) {
        if (!TextUtils.isEmpty(id) && configs != null) {
            for (AiModelConfig config : configs) if (config != null && id.equals(config.id)) return config;
        }
        return fallback;
    }

    private String taskEnvelope(String userTask, String extra) {
        String context = "";
        // Each run snapshots the saved reusable context so edits to memory do not change a queued task.
        // The context is informational and never overrides safety, platform or explicit user constraints.
        AgentRun run = null;
        String runId = getInputData().getString("run_id");
        if (!TextUtils.isEmpty(runId)) run = AgentRunStore.get(appContext, runId);
        if (run != null && !TextUtils.isEmpty(run.reusableContext)) {
            context = "\n\nREUSABLE PROJECT / AGENT CONTEXT:\n" + run.reusableContext.trim();
        }
        return "USER TASK:\n" + userTask.trim() + context + "\n\nWORKFLOW INSTRUCTIONS:\n" + extra +
                "\n\nEnvironment: Sketchware Neo on Android. Do not claim files were changed, a project was built, or tests passed unless the current workflow actually performed and verified those actions.";
    }

    private String request(AiModelConfig config, String systemPrompt, String userPrompt) throws InterruptedException {
        if (isStopped()) return null;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> response = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicBoolean delivered = new AtomicBoolean(false);
        AiManager.sendPrompt(appContext, config, systemPrompt, userPrompt, new AiResponseCallback() {
            @Override public void onSuccess(String value) {
                if (delivered.compareAndSet(false, true)) { response.set(value); latch.countDown(); }
            }
            @Override public void onFailure(String error) {
                if (delivered.compareAndSet(false, true)) { failure.set(error); latch.countDown(); }
            }
            @Override public void onError(String requestId, String error) { onFailure(error); }
            @Override public void onComplete(String requestId, String fullText) { onSuccess(fullText); }
        });
        long waited = 0L;
        while (waited < REQUEST_TIMEOUT_SECONDS && !isStopped()) {
            if (latch.await(1, TimeUnit.SECONDS)) break;
            waited++;
        }
        if (isStopped()) return null;
        if (latch.getCount() != 0) throw new IllegalStateException("Timed out waiting for " + config.displayName + " after " + REQUEST_TIMEOUT_SECONDS + " seconds.");
        if (!TextUtils.isEmpty(failure.get())) throw new IllegalStateException(config.displayName + ": " + failure.get());
        String value = response.get();
        if (TextUtils.isEmpty(value) || value.trim().isEmpty()) throw new IllegalStateException(config.displayName + " returned an empty response.");
        return value.trim();
    }

    private Result stoppedOrFailed(AgentRun run, String message) {
        if (isStopped()) return cancel(run);
        return fail(run, message);
    }

    private Result cancel(AgentRun run) {
        run.status = AgentRun.CANCELLED;
        run.record("Cancelled", "Task was stopped before all stages finished.");
        persist(run);
        return Result.failure();
    }

    private Result fail(AgentRun run, String message) {
        run.status = AgentRun.FAILED;
        run.errorMessage = message == null ? "Unknown AI workflow error" : message;
        run.record("Failed", run.errorMessage);
        persist(run);
        return Result.failure();
    }

    private void persist(AgentRun run) {
        try { AgentRunStore.save(appContext, run); }
        catch (RuntimeException e) {
            android.util.Log.e("AgentRunWorker", "Could not persist agent run state", e);
        }
        publishForeground(run);
    }

    /** Keep long model requests eligible to continue after the workspace screen is closed. */
    private void publishForeground(AgentRun run) {
        if (run == null || isStopped()) return;
        try {
            NotificationManager manager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                        "AI agent tasks", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Progress for AI agent tasks running in the background.");
                manager.createNotificationChannel(channel);
            }

            int notificationId = 0xA100000 | (run.id == null ? 1 : (run.id.hashCode() & 0x000FFFFF));
            Intent openWorkspace = new Intent(appContext, AgentWorkspaceActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(appContext, notificationId, openWorkspace,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            boolean running = AgentRun.RUNNING.equals(run.status) || AgentRun.QUEUED.equals(run.status);
            String title = running ? "Sketchware Neo · AI agents working" : "Sketchware Neo · AI agents";
            String detail = TextUtils.isEmpty(run.currentStage) ? "Working on your task" : run.currentStage;
            NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_mtrl_ai)
                    .setContentTitle(title)
                    .setContentText(detail)
                    .setContentIntent(contentIntent)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOnlyAlertOnce(true)
                    .setOngoing(running)
                    .setProgress(100, Math.max(0, Math.min(100, run.progress)), running);
            if (!TextUtils.isEmpty(run.title)) builder.setSubText(run.title);
            setForegroundAsync(new ForegroundInfo(notificationId, builder.build())).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Notification/foreground restrictions should be visible in logs without crashing the worker.
            android.util.Log.w("AgentRunWorker", "Could not update foreground notification", e);
        }
    }
}
