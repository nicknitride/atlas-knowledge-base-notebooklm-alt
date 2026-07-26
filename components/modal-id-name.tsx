"use client";
import { Button } from "./ui/button";

interface ModalProps {
  showModal: boolean;
  onSubmit: React.FormEventHandler<HTMLFormElement>;
  title: string;
  inputLabel: string;
  onCancel: () => void;
  labelPlaceHolder: string;
  labelValue: string;
  onChange: (value: string) => void;
}
export function ModalIdName({
  showModal,
  onSubmit,
  title,
  inputLabel,
  labelPlaceHolder,
  labelValue,
  onChange,
  onCancel,
}: ModalProps) {
  if (!showModal) return null;
  return (
    <>
      <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
        <div className="bg-card border border-border rounded-xl p-6 w-full max-w-sm shadow-2xl">
          <h3 className="text-base font-semibold text-foreground mb-4">
            {title}
          </h3>
          <form
            onSubmit={(e) => {
              onSubmit(e)
            }}
            className="space-y-4"
          >
            <div>
              <label className="text-xs text-muted-foreground block mb-1">
                {inputLabel}
              </label>
              <input
                type="text"
                value={labelValue}
                onChange={(e) => onChange(e.target.value)}
                placeholder={labelPlaceHolder}
                className="w-full px-3 py-2 rounded-lg bg-input border border-border text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/50"
                autoFocus
              />
            </div>
            <div className="flex gap-2 justify-end">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => {
                  onCancel();
                }}
              >
                Cancel
              </Button>
              <Button type="submit" size="sm" disabled={!labelValue.trim()}>
                Confirm
              </Button>
            </div>
          </form>
        </div>
      </div>
    </>
  );
}
