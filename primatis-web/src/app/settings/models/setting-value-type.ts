
export type SettingValueType = 'INTEGER' | 'DECIMAL';


export function isSupportedSettingValueType(value: string): value is SettingValueType {
  return value === 'INTEGER' || value === 'DECIMAL';
}
