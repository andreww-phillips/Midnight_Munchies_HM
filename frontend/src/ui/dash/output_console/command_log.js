import { createContext, useContext } from 'react';

const ignore = () => {};
export const CommandLogContext = createContext({ entries: [], record: ignore, changeIdentity: ignore });

export function useCommandLog() {
  return useContext(CommandLogContext);
}
