export function formatWaiting(seconds) {
  const minutes = Math.floor(seconds / 60);
  return `${minutes}m ${String(seconds % 60).padStart(2, '0')}s`;
}

export const shortId = (id) => (id ? id.slice(0, 8) : '');
