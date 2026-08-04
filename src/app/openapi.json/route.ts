import { NextResponse } from "next/server";
export async function GET(){return NextResponse.json({openapi:"3.1.0",info:{title:"Nexora AI API",version:"0.1.0"},paths:{"/api/health":{get:{summary:"Healthcheck"}},"/api/chat":{post:{summary:"Chat web"}},"/api/mobile/chat":{post:{summary:"Chat Android"}}}})}
