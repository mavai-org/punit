package org.mavai.punit.report;

/**
 * Shared HTML primitives for the report renderers in this module and its
 * sub-packages: HTML-text escaping and the base stylesheet.
 *
 * <p>Extracted so that every report — the test report
 * ({@link HtmlReportWriter}) and the per-experiment-type comparison
 * reports under {@code org.mavai.punit.report.*} — renders with one
 * identical colour palette, font stack, and table styling. A report that
 * appends its own chart styles emits {@link #appendBaseCss} first, then
 * its own {@code <style>} block.
 */
public final class ReportHtml {

    private ReportHtml() {
    }

    /**
     * Escapes the five characters that are unsafe in HTML text and
     * double-quoted attribute values. Null becomes the empty string.
     */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Appends the base report stylesheet — colour variables, font stack,
     * body layout, table styling, and the {@code <details>}/{@code
     * <summary>} idiom — wrapped in its own {@code <style>} element.
     */
    public static void appendBaseCss(StringBuilder html) {
        html.append("<style>\n");
        html.append("""
                :root {
                    --pass-color: #2e7d32;
                    --fail-color: #c62828;
                    --inconclusive-color: #6a1b9a;
                    --border-color: #dee2e6;
                    --bg-light: #f8f9fa;
                    --bg-white: #ffffff;
                    --text-color: #212529;
                    --text-muted: #6c757d;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    color: var(--text-color);
                    background: var(--bg-light);
                    padding: 2rem;
                    max-width: 1200px;
                    margin: 0 auto;
                }
                header { margin-bottom: 2rem; }
                h1 { font-size: 1.75rem; margin-bottom: 0.5rem; }
                h2 { font-size: 1.25rem; margin-bottom: 0.75rem; color: var(--text-muted); }
                .timestamp { color: var(--text-muted); font-size: 0.875rem; }
                .summary-stats {
                    margin-top: 1rem;
                    display: flex;
                    gap: 1.5rem;
                    font-size: 1rem;
                    font-weight: 600;
                }
                .stat { padding: 0.25rem 0.75rem; border-radius: 4px; }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    background: var(--bg-white);
                    border: 1px solid var(--border-color);
                    border-radius: 4px;
                    margin-bottom: 1.5rem;
                }
                thead th {
                    background: var(--bg-light);
                    padding: 0.75rem;
                    text-align: left;
                    font-size: 0.875rem;
                    border-bottom: 2px solid var(--border-color);
                }
                tbody td {
                    padding: 0.75rem;
                    border-bottom: 1px solid var(--border-color);
                    font-size: 0.875rem;
                    vertical-align: top;
                }
                tbody tr:last-child td { border-bottom: none; }
                details > summary {
                    cursor: pointer;
                    font-weight: 500;
                }
                details > summary:hover { text-decoration: underline; }
                pre.level2, pre.level3 {
                    margin: 0.5rem 0 0.5rem 1.5rem;
                    padding: 0.75rem;
                    background: var(--bg-light);
                    border: 1px solid var(--border-color);
                    border-left: 3px solid var(--border-color);
                    border-radius: 4px;
                    font-size: 0.8125rem;
                    overflow-x: auto;
                    white-space: pre-wrap;
                    word-wrap: break-word;
                }
                details details {
                    margin-left: 1.5rem;
                }
                pre.level3 {
                    overflow: visible;
                }
                pre.level3 span.tip {
                    border-bottom: 1px dotted var(--text-muted);
                    cursor: help;
                    position: relative;
                }
                pre.level3 span.tip:hover::after {
                    content: attr(data-tip);
                    position: absolute;
                    left: 0;
                    bottom: 1.4em;
                    background: #333;
                    color: #fff;
                    padding: 0.4em 0.6em;
                    border-radius: 4px;
                    font-size: 0.75rem;
                    white-space: normal;
                    max-width: 320px;
                    z-index: 10;
                    pointer-events: none;
                }
                .punit-pass { color: var(--pass-color); font-weight: 600; }
                .punit-fail { color: var(--fail-color); font-weight: 600; }
                .punit-inconclusive { color: var(--inconclusive-color); font-weight: 600; }
                .per-criterion { padding: 0.5rem 0 0.25rem 0.5rem; }
                .criterion-block { margin: 0.25rem 0 0.75rem 0; }
                .criterion-block h4 {
                    font-size: 0.875rem;
                    font-family: monospace;
                    margin-bottom: 0.25rem;
                }
                .criterion-block dl {
                    display: grid;
                    grid-template-columns: max-content 1fr;
                    column-gap: 1rem;
                    row-gap: 0.1rem;
                    font-size: 0.8125rem;
                    margin-left: 0.5rem;
                }
                .criterion-block dt { color: var(--text-muted); }
                .criterion-block dd { font-family: monospace; }
                .latency-observed { color: #adb5bd; }
                .latency-pass { color: var(--pass-color); font-weight: 600; }
                .latency-fail { color: var(--fail-color); font-weight: 600; }
                .test-group { margin-bottom: 2rem; }
                .banner-inconclusive {
                    margin-top: 1rem;
                    padding: 0.75rem 1rem;
                    background: #f3e5f5;
                    border: 1px solid var(--inconclusive-color);
                    border-left: 4px solid var(--inconclusive-color);
                    border-radius: 4px;
                    font-size: 0.875rem;
                    color: var(--text-color);
                }
                .banner-inconclusive code {
                    background: var(--bg-white);
                    padding: 0.1rem 0.4rem;
                    border-radius: 3px;
                    font-size: 0.8125rem;
                }
                .misalignment-guidance {
                    margin: 0.5rem 0 0.5rem 1.5rem;
                    padding: 0.5rem 0.75rem;
                    background: #f3e5f5;
                    border: 1px solid var(--inconclusive-color);
                    border-left: 3px solid var(--inconclusive-color);
                    border-radius: 4px;
                    font-size: 0.8125rem;
                }
                .misalignment-guidance code {
                    background: var(--bg-white);
                    padding: 0.1rem 0.4rem;
                    border-radius: 3px;
                }
                table.postcondition-failures {
                    margin: 0.5rem 0 0.5rem 1.5rem;
                    border-collapse: collapse;
                    font-size: 0.8125rem;
                    background: var(--bg-white);
                }
                table.postcondition-failures th,
                table.postcondition-failures td {
                    padding: 0.4rem 0.6rem;
                    border: 1px solid var(--border-color);
                    vertical-align: top;
                    text-align: left;
                }
                table.postcondition-failures th {
                    background: #f8f9fa;
                    font-weight: 600;
                    color: var(--text-muted);
                }
                table.postcondition-failures td.clause { font-weight: 500; }
                table.postcondition-failures td.count {
                    text-align: right;
                    font-variant-numeric: tabular-nums;
                    color: var(--fail-color);
                    font-weight: 600;
                }
                table.postcondition-failures td.exemplars ul {
                    margin: 0;
                    padding-left: 1rem;
                }
                table.postcondition-failures td.exemplars li { margin: 0.1rem 0; }
                table.postcondition-failures td.exemplars code {
                    background: #f3f4f6;
                    padding: 0.05rem 0.3rem;
                    border-radius: 3px;
                }
                table.postcondition-failures .no-exemplars {
                    color: var(--text-muted);
                    font-style: italic;
                }
                .assumptions {
                    margin-bottom: 1.5rem;
                    background: var(--bg-white);
                    border: 1px solid var(--border-color);
                    border-radius: 4px;
                    font-size: 0.875rem;
                }
                .assumptions > summary {
                    padding: 0.75rem 1rem;
                    cursor: pointer;
                    font-weight: 600;
                    color: var(--text-muted);
                }
                .assumptions > summary:hover { color: var(--text-color); }
                .assumptions-body {
                    padding: 0.75rem 1rem 1rem;
                    border-top: 1px solid var(--border-color);
                    line-height: 1.6;
                }
                .assumptions-body p { margin-bottom: 0.75rem; }
                .assumptions-body ul {
                    margin: 0 0 0.75rem 1.5rem;
                    list-style-type: disc;
                }
                .assumptions-body li { margin-bottom: 0.35rem; }
                .assumptions-warning {
                    padding: 0.5rem 0.75rem;
                    background: #fff8e1;
                    border-left: 3px solid #f9a825;
                    border-radius: 4px;
                }
                footer {
                    margin-top: 2rem;
                    padding-top: 1rem;
                    border-top: 1px solid var(--border-color);
                    color: var(--text-muted);
                    font-size: 0.8125rem;
                }
                """);
        html.append("</style>\n");
    }
}
