import { NextRequest,NextResponse } from "next/server";
import { z } from "zod";
import { runAgent } from "@/lib/agent";
const schema=z.object({message:z.string().min(1).max(32000),mode:z.enum(["auto","fullstack","android","backend","security","data","devops"]).default("auto")});
export async function POST(req:NextRequest){const body=schema.parse(await req.json());return NextResponse.json({ok:true,...await runAgent(body.message,body.mode)})}
