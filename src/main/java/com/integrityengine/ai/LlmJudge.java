package com.integrityengine.ai;

import com.integrityengine.domain.CodeSubmission;
import java.util.Optional;

/**
 * An external model asked to judge whether a submission looks machine-written.
 *
 * <p>Deliberately an interface. Everything else in this project is deterministic and
 * offline; this one call is a network dependency with a non-deterministic response, and
 * isolating it behind a seam is what makes the rest of the module testable.
 *
 * <p><b>Implementations must never throw.</b> Any failure — timeout, HTTP error,
 * malformed body, nonsense values — is reported as an empty result, and the caller
 * degrades to the heuristic signal alone. The API key is passed per call and must never
 * be stored, logged, or echoed into a result.
 */
public interface LlmJudge {

    /**
     * @param apiKey     caller-supplied key, used for this call only
     * @param submission the submission to judge
     * @return the verdict, or empty if the call failed or the response was unusable
     */
    Optional<LlmVerdict> judge(String apiKey, CodeSubmission submission);
}
