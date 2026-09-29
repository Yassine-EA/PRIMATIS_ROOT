import { NotificationStatus } from './notification-status';
import { NotificationType } from './notification-type';


export interface NotificationResponse {
  id: number;
  notificationType: NotificationType;
  notificationStatus: NotificationStatus;
  title: string;
  message: string;
  originId: number;
  createdAt: string;
  readAt: string | null;
}
