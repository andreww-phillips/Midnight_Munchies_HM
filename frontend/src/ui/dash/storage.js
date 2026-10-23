export function storageUrl(_bucket, path) {
  if (!path) return '';
  return /^(https?:\/\/|data:|\/)/i.test(path) ? path : '';
}

export function dishImageUrl(_restaurantName, path) {
  return storageUrl('', path);
}
