import { SettingUpdatedByResponse } from './setting-updated-by-response';
import { SettingValueType } from './setting-value-type';


export interface SettingResponse {
  readonly settingKey: string;
  readonly settingValue: string;
  readonly valueType: SettingValueType;
  readonly description: string;
  readonly updatedAt: string | null;
  readonly updatedByUser: SettingUpdatedByResponse | null;
}
