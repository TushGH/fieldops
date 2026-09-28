import {RequestPreview} from "@/components/request-preview";
export default async function Page({searchParams}:{searchParams:Promise<{provider?:string}>}){const {provider}=await searchParams;return <RequestPreview slug={typeof provider==="string"?provider:""}/>;}
