// Port of RunConsoleTap.containsErrorSignature. A cheap pre-filter: only run the diagnosis engine
// over a log chunk that carries a recognisable error marker. Connection failures are logged at WARN
// with no exception, so their markers are recognised too.
const STARTUP_FAILURE_MARKER = 'APPLICATION FAILED TO START';
const RUNTIME_EXCEPTION_MARKER = 'Exception in thread';

export function containsErrorSignature(text: string | null): boolean {
  if (text === null) return false;
  return (
    text.includes(STARTUP_FAILURE_MARKER) ||
    text.includes(RUNTIME_EXCEPTION_MARKER) ||
    text.includes('Caused by:') ||
    (text.includes('ERROR') && text.includes('Exception')) ||
    text.includes('could not be established') ||
    text.includes('Failed to update metadata') ||
    text.includes('No endpoint ') ||
    text.includes('Resolved [')
  );
}
