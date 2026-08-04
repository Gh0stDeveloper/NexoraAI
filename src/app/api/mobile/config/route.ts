import { NextResponse } from "next/server";
import { env } from "@/lib/env";
export async function GET(){return NextResponse.json({ok:true,app:env.appName,version:env.appVersion,apiUrl:env.mobileApi,features:["chat","docs","workspaces","training","vps"]})}
