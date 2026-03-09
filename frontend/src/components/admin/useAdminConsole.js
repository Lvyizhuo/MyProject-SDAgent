import { useContext } from 'react';
import { AdminConsoleContext } from './adminConsoleContext';

export const useAdminConsole = () => {
    const context = useContext(AdminConsoleContext);
    if (!context) {
        throw new Error('useAdminConsole 必须在 AdminConsoleProvider 内使用');
    }
    return context;
};