import React, {
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState
} from 'react';
import {
    AlertCircle,
    CheckCircle2,
    Info,
    TriangleAlert,
    X
} from 'lucide-react';
import { addNotification } from '../../utils/notificationCenter';
import { AdminConsoleContext } from './adminConsoleContext';
import './AdminConsoleFeedback.css';

const TOAST_DURATION = {
    success: 3200,
    info: 3000,
    warning: 4200,
    error: 5200
};

const getToastIcon = (type) => {
    switch (type) {
        case 'success':
            return <CheckCircle2 size={18} />;
        case 'warning':
            return <TriangleAlert size={18} />;
        case 'error':
            return <AlertCircle size={18} />;
        default:
            return <Info size={18} />;
    }
};

const ToastViewport = ({ toasts, onDismiss }) => {
    return (
        <div className="admin-toast-viewport" aria-live="polite" aria-atomic="false">
            {toasts.map((toast) => (
                <div key={toast.id} className={`admin-toast ${toast.type || 'info'}`}>
                    <div className="admin-toast-icon">{getToastIcon(toast.type)}</div>
                    <div className="admin-toast-content">
                        {toast.title && <strong>{toast.title}</strong>}
                        <span>{toast.text}</span>
                    </div>
                    <button
                        type="button"
                        className="admin-toast-close"
                        onClick={() => onDismiss(toast.id)}
                        aria-label="关闭提示"
                    >
                        <X size={14} />
                    </button>
                </div>
            ))}
        </div>
    );
};

const ConfirmDialog = ({ dialog, onClose }) => {
    if (!dialog) {
        return null;
    }

    return (
        <div className="admin-overlay" onClick={() => onClose(false)}>
            <div className="admin-dialog admin-dialog-confirm" onClick={(event) => event.stopPropagation()}>
                <div className="admin-dialog-header">
                    <div>
                        <h3>{dialog.title || '确认操作'}</h3>
                        {dialog.message && <p>{dialog.message}</p>}
                    </div>
                    <button type="button" className="admin-dialog-close" onClick={() => onClose(false)} aria-label="关闭确认弹层">
                        <X size={18} />
                    </button>
                </div>
                <div className="admin-dialog-footer">
                    <button type="button" className="admin-btn admin-btn-secondary" onClick={() => onClose(false)}>
                        {dialog.cancelText || '取消'}
                    </button>
                    <button
                        type="button"
                        className={`admin-btn ${dialog.tone === 'danger' ? 'admin-btn-danger' : 'admin-btn-primary'}`}
                        onClick={() => onClose(true)}
                    >
                        {dialog.confirmText || '确认'}
                    </button>
                </div>
            </div>
        </div>
    );
};

const PromptDialog = ({ dialog, value, onChange, onClose }) => {
    if (!dialog) {
        return null;
    }

    return (
        <div className="admin-overlay" onClick={() => onClose(null)}>
            <div className="admin-dialog admin-dialog-prompt" onClick={(event) => event.stopPropagation()}>
                <div className="admin-dialog-header">
                    <div>
                        <h3>{dialog.title || '请输入内容'}</h3>
                        {dialog.message && <p>{dialog.message}</p>}
                    </div>
                    <button type="button" className="admin-dialog-close" onClick={() => onClose(null)} aria-label="关闭输入弹层">
                        <X size={18} />
                    </button>
                </div>
                <div className="admin-dialog-body">
                    {dialog.label && <label className="admin-dialog-label">{dialog.label}</label>}
                    <input
                        autoFocus
                        type="text"
                        className="admin-dialog-input"
                        value={value}
                        onChange={(event) => onChange(event.target.value)}
                        placeholder={dialog.placeholder || ''}
                        onKeyDown={(event) => {
                            if (event.key === 'Enter') {
                                onClose(value);
                            }
                        }}
                    />
                </div>
                <div className="admin-dialog-footer">
                    <button type="button" className="admin-btn admin-btn-secondary" onClick={() => onClose(null)}>
                        {dialog.cancelText || '取消'}
                    </button>
                    <button type="button" className="admin-btn admin-btn-primary" onClick={() => onClose(value)}>
                        {dialog.confirmText || '确认'}
                    </button>
                </div>
            </div>
        </div>
    );
};

export const AdminConsoleProvider = ({ children }) => {
    const [toasts, setToasts] = useState([]);
    const [confirmDialog, setConfirmDialog] = useState(null);
    const [promptDialog, setPromptDialog] = useState(null);
    const [promptValue, setPromptValue] = useState('');
    const toastIdRef = useRef(0);
    const toastTimersRef = useRef(new Map());
    const confirmResolverRef = useRef(null);
    const promptResolverRef = useRef(null);

    useEffect(() => {
        const timers = toastTimersRef.current;
        return () => {
            timers.forEach((timerId) => window.clearTimeout(timerId));
            timers.clear();
        };
    }, []);

    const dismissToast = useCallback((toastId) => {
        const timerId = toastTimersRef.current.get(toastId);
        if (timerId) {
            window.clearTimeout(timerId);
            toastTimersRef.current.delete(toastId);
        }
        setToasts((current) => current.filter((item) => item.id !== toastId));
    }, []);

    const notify = useCallback((payload) => {
        const options = typeof payload === 'string' ? { text: payload } : payload;
        const type = options.type || 'info';
        const source = options.source || '管理员控制台';
        const id = `admin-toast-${Date.now()}-${toastIdRef.current += 1}`;
        const toast = {
            id,
            title: options.title || source,
            text: options.text,
            type
        };

        addNotification({
            text: options.text,
            type,
            source
        });

        setToasts((current) => [...current, toast].slice(-4));
        const duration = options.duration ?? TOAST_DURATION[type] ?? 3200;
        const timerId = window.setTimeout(() => {
            dismissToast(id);
        }, duration);
        toastTimersRef.current.set(id, timerId);
    }, [dismissToast]);

    const confirm = useCallback((options) => {
        return new Promise((resolve) => {
            confirmResolverRef.current = resolve;
            setConfirmDialog({
                title: options?.title,
                message: options?.message,
                confirmText: options?.confirmText,
                cancelText: options?.cancelText,
                tone: options?.tone
            });
        });
    }, []);

    const closeConfirm = useCallback((result) => {
        setConfirmDialog(null);
        if (confirmResolverRef.current) {
            confirmResolverRef.current(result);
            confirmResolverRef.current = null;
        }
    }, []);

    const requestInput = useCallback((options) => {
        return new Promise((resolve) => {
            promptResolverRef.current = resolve;
            setPromptValue(options?.initialValue || '');
            setPromptDialog({
                title: options?.title,
                message: options?.message,
                label: options?.label,
                placeholder: options?.placeholder,
                confirmText: options?.confirmText,
                cancelText: options?.cancelText
            });
        });
    }, []);

    const closePrompt = useCallback((result) => {
        setPromptDialog(null);
        if (promptResolverRef.current) {
            promptResolverRef.current(result);
            promptResolverRef.current = null;
        }
    }, []);

    useEffect(() => {
        if (!confirmDialog && !promptDialog) {
            return undefined;
        }

        const handleKeyDown = (event) => {
            if (event.key !== 'Escape') {
                return;
            }

            if (promptDialog) {
                closePrompt(null);
                return;
            }

            if (confirmDialog) {
                closeConfirm(false);
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [closeConfirm, closePrompt, confirmDialog, promptDialog]);

    const contextValue = useMemo(() => ({
        notify,
        confirm,
        prompt: requestInput
    }), [confirm, notify, requestInput]);

    return (
        <AdminConsoleContext.Provider value={contextValue}>
            {children}
            <ToastViewport toasts={toasts} onDismiss={dismissToast} />
            <ConfirmDialog dialog={confirmDialog} onClose={closeConfirm} />
            <PromptDialog
                dialog={promptDialog}
                value={promptValue}
                onChange={setPromptValue}
                onClose={closePrompt}
            />
        </AdminConsoleContext.Provider>
    );
};