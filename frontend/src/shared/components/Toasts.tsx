import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { Icon } from './Icon';

interface Toast {
  id: string;
  title: string;
  body?: string;
  c?: string;
  icon?: string;
}

interface ToastCtx {
  toast: (title: string, body?: string, c?: string, icon?: string) => void;
}

const Ctx = createContext<ToastCtx | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Toast[]>([]);

  const toast = useCallback((title: string, body?: string, c?: string, icon?: string) => {
    const id = Math.random().toString(36).slice(2);
    setItems((ts) => [...ts.slice(-2), { id, title, body, c, icon }]);
    setTimeout(() => setItems((ts) => ts.filter((t) => t.id !== id)), 5200);
  }, []);

  const dismiss = (id: string) => setItems((ts) => ts.filter((t) => t.id !== id));

  return (
    <Ctx.Provider value={{ toast }}>
      {children}
      <div className="k-toasts">
        {items.map((t) => (
          <div key={t.id} className="k-toast" style={{ '--c': t.c || 'var(--color-accent)' } as React.CSSProperties} onClick={() => dismiss(t.id)}>
            <Icon n={t.icon || 'bell'} s={15} c="var(--c)" />
            <div>
              <div className="k-toast-t">{t.title}</div>
              {t.body && <div className="k-toast-b mono">{t.body}</div>}
            </div>
          </div>
        ))}
      </div>
    </Ctx.Provider>
  );
}

export function useToast() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useToast, ToastProvider içinde kullanılmalı');
  return ctx.toast;
}
