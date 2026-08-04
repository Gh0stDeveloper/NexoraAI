import { NextResponse } from "next/server";
export async function GET(){return NextResponse.json({ok:true,docs:["inicio","vps","dominios","android","ci","seguridad"]})}
