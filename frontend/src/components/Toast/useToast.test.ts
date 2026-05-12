import { beforeEach, describe, expect, it } from 'vitest';
import { useToast, emitToast } from './useToast';

describe('useToast', () => {
  beforeEach(() => {
    const { toasts } = useToast();
    toasts.value.splice(0, toasts.value.length);
  });

  it('showToast pushes a new entry to the queue', () => {
    const { toasts, showToast } = useToast();
    showToast('hello', 'info');
    expect(toasts.value).toHaveLength(1);
    expect(toasts.value[0].message).toBe('hello');
    expect(toasts.value[0].type).toBe('info');
    expect(toasts.value[0].duration).toBe(4000);
  });

  it('defaults type to info', () => {
    const { toasts, showToast } = useToast();
    showToast('msg');
    expect(toasts.value[0].type).toBe('info');
  });

  it('emitToast also pushes to the shared queue', () => {
    const { toasts } = useToast();
    emitToast('from module', 'error');
    expect(toasts.value).toHaveLength(1);
    expect(toasts.value[0].type).toBe('error');
  });

  it('dismissToast removes the entry by id', () => {
    const { toasts, showToast, dismissToast } = useToast();
    showToast('a');
    showToast('b');
    const firstId = toasts.value[0].id;
    dismissToast(firstId);
    expect(toasts.value).toHaveLength(1);
    expect(toasts.value[0].message).toBe('b');
  });

  it('assigns unique increasing ids', () => {
    const { toasts, showToast } = useToast();
    showToast('a');
    showToast('b');
    expect(toasts.value[0].id).not.toBe(toasts.value[1].id);
    expect(toasts.value[1].id).toBeGreaterThan(toasts.value[0].id);
  });
});
