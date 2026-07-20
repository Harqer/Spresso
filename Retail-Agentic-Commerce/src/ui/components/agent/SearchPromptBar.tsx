"use client";

import { useCallback, useRef } from "react";
import type { ChangeEvent, FormEvent } from "react";
import { Camera } from "@/components/icons";

interface SearchPromptBarProps {
  value: string;
  onChange: (value: string) => void;
  onSubmit: (value: string) => void;
  onPhotoUpload?: (base64: string) => void;
  placeholder?: string;
}

export function SearchPromptBar({
  value,
  onChange,
  onSubmit,
  onPhotoUpload,
  placeholder = "show me some shoes"
}: SearchPromptBarProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      if (value.trim().length === 0) return;
      onSubmit(value);
    },
    [onSubmit, value]
  );

  const handleChange = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      onChange(event.target.value);
    },
    [onChange]
  );

  const handleCameraClick = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  const handleFileChange = useCallback(async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file && onPhotoUpload) {
      const reader = new FileReader();
      reader.onloadend = () => {
        const base64 = reader.result as string;
        // Remove data:image/...;base64, prefix
        const base64Data = base64.split(",")[1];
        onPhotoUpload(base64Data);
      };
      reader.readAsDataURL(file);
    }
    // Reset input so the same file can be uploaded again if needed
    event.target.value = "";
  }, [onPhotoUpload]);

  const isDisabled = value.trim().length === 0;

  return (
    <div className="flex flex-col gap-2 w-full">
      <form
        onSubmit={handleSubmit}
        className="flex w-full items-center gap-2"
        aria-label="Search products"
      >
        <div className="nv-input nv-text-input-root flex-1">
          <input
            type="search"
            value={value}
            onChange={handleChange}
            placeholder={placeholder}
            className="nv-text-input-element px-4 placeholder:text-white/45"
            aria-label="Search query"
          />
        </div>

        {onPhotoUpload && (
          <>
            <input
              type="file"
              accept="image/*"
              className="hidden"
              ref={fileInputRef}
              onChange={handleFileChange}
            />
            <button
              type="button"
              onClick={handleCameraClick}
              className="p-2.5 rounded-lg border border-white/10 bg-white/5 hover:bg-white/10 transition-colors text-white/70 hover:text-white"
              aria-label="Upload photo for try-on"
            >
              <Camera className="w-5 h-5" />
            </button>
          </>
        )}

        <button
          type="submit"
          className="nv-button nv-button--primary nv-button--brand text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50"
          disabled={isDisabled}
        >
          Send
        </button>
      </form>
    </div>
  );
}
