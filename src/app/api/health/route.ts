import { NextResponse } from "next/server";
import { env } from "@/lib/env";
export async function GET(){return NextResponse.json({ok:true,app:env.appName,version:env.appVersion,environment:env.appEnv,provider:env.aiProvider,model:env.model,siteUrl:env.siteUrl,apiUrl:env.apiUrl,security:{dangerousTools:env.dangerousTools,codeExecution:env.codeExecution,githubWrite:env.githubWrite},timestamp:new Date().toISOString()})}
