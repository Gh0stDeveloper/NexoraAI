create extension if not exists vector;
create table if not exists workspaces(id uuid primary key default gen_random_uuid(), name text not null, type text not null default 'project', created_at timestamptz default now());
create table if not exists conversations(id uuid primary key default gen_random_uuid(), workspace_id uuid references workspaces(id), title text, created_at timestamptz default now());
create table if not exists messages(id uuid primary key default gen_random_uuid(), conversation_id uuid references conversations(id), role text not null, content text not null, created_at timestamptz default now());
