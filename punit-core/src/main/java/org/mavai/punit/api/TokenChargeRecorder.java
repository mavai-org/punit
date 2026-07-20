package org.mavai.punit.api;

/**
 * Interface for recording token consumption during dynamic token charging.
 * 
 * <p>When a test method declares a parameter of this type, the framework
 * switches to dynamic token charging mode. The test implementation is
 * responsible for calling {@link #recordTokens(int)} or {@link #recordTokens(long)}
 * to report actual token consumption from external services.
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @ProbabilisticTest(samples = 50, minPassRate = 0.90, tokenBudget = 100000)
 * void llmRespondsWithValidJson(TokenChargeRecorder tokenRecorder) {
 *     LlmResponse response = llmClient.complete("Generate JSON...");
 *     
 *     // Report actual token consumption from LLM response metadata
 *     tokenRecorder.recordTokens(response.getUsage().getTotalTokens());
 *     
 *     assertThat(response.getContent()).satisfies(JsonValidator::isValidJson);
 * }
 * }</pre>
 * 
 * <h2>Dynamic vs Static Token Charging</h2>
 * <ul>
 *   <li><strong>Dynamic</strong>: Enabled when test method has a TokenChargeRecorder parameter.
 *       Tokens are accumulated via recordTokens() calls.</li>
 *   <li><strong>Static</strong>: Enabled when tokenCharge > 0 AND no TokenChargeRecorder parameter.
 *       Fixed tokenCharge is added after each sample.</li>
 * </ul>
 * 
 * <p>If both are present, dynamic mode takes precedence and static tokenCharge is ignored.
 * 
 * @see ProbabilisticTest#tokenBudget()
 * @see ProbabilisticTest#tokenCharge()
 */
// mavai-ref: JVI-76VA511 — do not remove (resolves in mavai-orchestrator)
public interface TokenChargeRecorder {
    
    /**
     * Records tokens consumed during this sample invocation.
     * May be called multiple times per sample; values are accumulated.
     * 
     * @param tokens number of tokens to add to this sample's consumption (must be ≥ 0)
     * @throws IllegalArgumentException if tokens &lt; 0
     */
    void recordTokens(int tokens);
    
    /**
     * Records tokens consumed during this sample invocation.
     * Convenience overload for long values (e.g., from API responses).
     * 
     * @param tokens number of tokens to add to this sample's consumption (must be ≥ 0)
     * @throws IllegalArgumentException if tokens &lt; 0
     */
    void recordTokens(long tokens);
    
    /**
     * Returns the total tokens recorded so far for this sample.
     * Resets to 0 at the start of each sample.
     * 
     * @return tokens recorded in current sample
     */
    long getTokensForCurrentSample();
    
    /**
     * Returns the cumulative tokens consumed across all completed samples.
     * Does not include the current (in-progress) sample.
     * 
     * @return total tokens consumed in completed samples
     */
    long getTotalTokensConsumed();
    
    /**
     * Returns the remaining token budget, or Long.MAX_VALUE if unlimited.
     * 
     * @return remaining budget after completed samples
     */
    long getRemainingBudget();
}

