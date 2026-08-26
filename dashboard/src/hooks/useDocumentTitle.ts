import { useEffect } from 'react';

/**
 * Custom hook to set document title dynamically.
 * Automatically appends " | 01flux WA" suffix.
 */
export function useDocumentTitle(title: string) {
  useEffect(() => {
    const previousTitle = document.title;
    document.title = `${title} | 01flux WA`;

    return () => {
      document.title = previousTitle;
    };
  }, [title]);
}
