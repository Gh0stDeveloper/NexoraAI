import { NextResponse } from "next/server";
export async function GET(){return NextResponse.json({ok:true,status:"ready",checks:["api","android","vps","ci"]})}
