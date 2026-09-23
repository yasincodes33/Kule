import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { RoleBadge, StatusTag } from './primitives';

describe('StatusTag', () => {
  it('durum etiketini yazdırır', () => {
    render(<StatusTag status="RUNNING" />);
    expect(screen.getByText('RUNNING')).toBeInTheDocument();
  });
});

describe('RoleBadge', () => {
  it('rol adını yazdırır', () => {
    render(<RoleBadge role="OWNER" />);
    expect(screen.getByText('OWNER')).toBeInTheDocument();
  });
});
