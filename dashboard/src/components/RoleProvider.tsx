import { useState, useCallback, type ReactNode } from 'react';
import type { UserRole, RoleContextType } from '../types/role';
import { RoleContext } from '../hooks/useRole';

export function RoleProvider({ children }: { children: ReactNode }) {
  const [role, setRoleState] = useState<UserRole | null>(() => {
    const saved = localStorage.getItem('flux_wa_user_role');
    return (saved as UserRole) || null;
  });

  const setRole = useCallback((newRole: UserRole | null) => {
    setRoleState(newRole);
    if (newRole) {
      localStorage.setItem('flux_wa_user_role', newRole);
    } else {
      localStorage.removeItem('flux_wa_user_role');
    }
  }, []);

  const value: RoleContextType = {
    role,
    setRole,
    isAdmin: role === 'admin',
    isOperator: role === 'operator',
    isViewer: role === 'viewer',
    canWrite: role === 'admin' || role === 'operator',
  };

  return <RoleContext.Provider value={value}>{children}</RoleContext.Provider>;
}
