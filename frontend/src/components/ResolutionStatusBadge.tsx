import React from 'react';
import { ResolutionStatus } from '../types';

interface Props {
  status: ResolutionStatus;
  size?: 'sm' | 'md' | 'lg';
}

export const ResolutionStatusBadge: React.FC<Props> = ({ status, size = 'md' }) => {
  const configs: Record<ResolutionStatus, { label: string; icon: string; bg: string; border: string; text: string; desc: string }> = {
    ANSWERED: {
      label: 'ANSWERED',
      icon: '✓',
      bg: 'rgba(16, 185, 129, 0.15)',
      border: '#10b981',
      text: '#34d399',
      desc: 'Substantiated by consistent, authoritative evidence.'
    },
    ANSWERED_WITH_QUALIFICATION: {
      label: 'ANSWERED WITH QUALIFICATION',
      icon: '⚖️',
      bg: 'rgba(59, 130, 246, 0.15)',
      border: '#3b82f6',
      text: '#60a5fa',
      desc: 'Validated with deterministic supersession or scope qualification.'
    },
    CONFLICT: {
      label: 'CONFLICT (ABSTAINED)',
      icon: '⚠️',
      bg: 'rgba(239, 68, 68, 0.18)',
      border: '#ef4444',
      text: '#f87171',
      desc: 'Unresolvable contradiction between equally authoritative policies. Preserved without blending.'
    },
    INSUFFICIENT_EVIDENCE: {
      label: 'INSUFFICIENT EVIDENCE',
      icon: '∅',
      bg: 'rgba(100, 116, 139, 0.2)',
      border: '#64748b',
      text: '#cbd5e1',
      desc: 'Retrieved evidence is empty or lacks required factual grounding.'
    }
  };

  const cfg = configs[status] || configs.INSUFFICIENT_EVIDENCE;

  const fontSizes = {
    sm: '0.75rem',
    md: '0.875rem',
    lg: '1rem'
  };

  const paddings = {
    sm: '0.2rem 0.55rem',
    md: '0.35rem 0.85rem',
    lg: '0.5rem 1.15rem'
  };

  return (
    <div
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '0.5rem',
        padding: paddings[size],
        background: cfg.bg,
        border: `1.5px solid ${cfg.border}`,
        borderRadius: '9999px',
        color: cfg.text,
        fontSize: fontSizes[size],
        fontWeight: 700,
        letterSpacing: '0.04em',
        fontFamily: 'var(--font-mono, monospace)',
        boxShadow: `0 0 12px ${cfg.bg}`
      }}
      title={cfg.desc}
    >
      <span>{cfg.icon}</span>
      <span>{cfg.label}</span>
    </div>
  );
};
