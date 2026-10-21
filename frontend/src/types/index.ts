export interface ServiceHealth {
  status: 'UP' | 'DOWN' | 'CHECKING';
  service?: string;
  version?: string;
  timestamp?: string;
  details?: Record<string, string>;
  error?: string;
}

export interface SystemStatusState {
  backend: ServiceHealth;
  aiService: ServiceHealth;
  database: ServiceHealth;
}
