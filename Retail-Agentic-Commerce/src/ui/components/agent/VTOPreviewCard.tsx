"use client";

import Image from "next/image";
import { Card, CardRoot, CardContent, Text, Flex, Stack } from "@kui/foundations-react-external";
import { Share } from "@/components/icons";

interface VTOPreviewCardProps {
  imageUrl: string;
  videoUrl?: string | null;
}

/**
 * VTO Preview Card for Web Frontend
 * Displays the AI-generated virtual try-on result with a premium "glass" aesthetic
 */
export function VTOPreviewCard({ imageUrl, videoUrl }: VTOPreviewCardProps) {
  return (
    <div className="w-full max-w-lg mx-auto my-4 animate-in fade-in slide-in-from-bottom-4 duration-500">
      <CardRoot className="overflow-hidden border-white/10 bg-black/40 backdrop-blur-xl">
        <div className="relative aspect-[3/4] w-full bg-neutral-900 flex items-center justify-center">
          {/* Main VTO Image */}
          <img
            src={imageUrl}
            alt="Virtual Try-On Result"
            className="h-full w-full object-contain"
          />

          {/* AI Badge */}
          <div className="absolute top-4 right-4">
            <div className="px-2 py-1 rounded-full bg-black/60 backdrop-blur-md border border-white/10">
              <span className="text-[10px] font-bold text-white uppercase tracking-wider">AI Generated</span>
            </div>
          </div>
        </div>

        <CardContent className="p-4 bg-white/5">
          <Flex align="center" justify="between">
            <Stack gap="1">
              <Text kind="label/bold/md" className="text-white">
                Virtual Try-On
              </Text>
              <Text kind="body/regular/xs" className="text-white/50">
                Powered by Vertex AI Imagen 3
              </Text>
            </Stack>

            <button
              className="p-2 rounded-full hover:bg-white/10 transition-colors text-white/70 hover:text-white"
              aria-label="Share try-on"
            >
              <Share className="w-5 h-5" />
            </button>
          </Flex>
        </CardContent>
      </CardRoot>
    </div>
  );
}
