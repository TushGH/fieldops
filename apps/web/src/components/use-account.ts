"use client";
import {useEffect,useState} from "react";
import {api,ApiError,rememberUser,type User} from "@/lib/identity-api";
export function useAccount(){
 const [user,setUser]=useState<User>();const [error,setError]=useState("");const [retry,setRetry]=useState(0);
 useEffect(()=>{const controller=new AbortController();api<User>("auth/me",{signal:controller.signal}).then(user=>{if(controller.signal.aborted)return;rememberUser(user.id);if(!user.emailVerifiedAt){window.location.replace("/verify-email");return;}setUser(user);setError("");}).catch(e=>{if(controller.signal.aborted)return;if(e instanceof ApiError&&e.status===401)window.location.replace("/login");else setError(e instanceof Error?e.message:"Unable to load your account.");});return()=>controller.abort();},[retry]);
 return {user,error,retry:()=>setRetry(n=>n+1)};
}
