import { NextRequest,NextResponse } from "next/server";
import { z } from "zod";
import { runAgent } from "@/lib/agent";
const schema=z.object({message:z.string().min(1).max(32000),mode:z.enum(["auto","fullstack","android","backend","security","data","devops"]).default("auto"),projectId:z.string().optional(),client:z.string().default("android")});
export async function POST(req:NextRequest){const b=schema.parse(await req.json());const msg=b.projectId?`[${b.projectId}]\n${b.message}`:b.message;return NextResponse.json({ok:true,client:b.client,projectId:b.projectId??null,...await runAgent(msg,b.mode)})}
