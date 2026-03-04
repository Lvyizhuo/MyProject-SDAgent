const STORAGE_KEY = 'user_notification_center_v1';
const MAX_ITEMS = 200;
const UPDATE_EVENT = 'notification-center-updated';

const safeParse = (raw) => {
    if (!raw) return [];
    try {
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
};

export const getNotifications = () => {
    return safeParse(localStorage.getItem(STORAGE_KEY));
};

const saveNotifications = (items) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
    window.dispatchEvent(new CustomEvent(UPDATE_EVENT));
};

export const addNotification = ({ text, type = 'info', source = '系统' }) => {
    const item = {
        id: `notice-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        text,
        type,
        source,
        createdAt: new Date().toISOString(),
        read: false
    };
    const next = [item, ...getNotifications()].slice(0, MAX_ITEMS);
    saveNotifications(next);
    return item;
};

export const markAllNotificationsRead = () => {
    const next = getNotifications().map(item => ({ ...item, read: true }));
    saveNotifications(next);
};

export const clearNotifications = () => {
    saveNotifications([]);
};

export const getUnreadNotificationCount = () => {
    return getNotifications().filter(item => !item.read).length;
};

export const notificationUpdateEvent = UPDATE_EVENT;
