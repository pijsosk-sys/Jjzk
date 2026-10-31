# Behaves like the official plugin's Main.server.luau + MockWebSocketService: long-poll GET /request,
# 423 = nothing yet, run the tool, POST /response {success, response, id}.
import json,sys,threading,time,urllib.request,urllib.error
URI='http://localhost:44755'
def tool(args):
 name=next(iter(args));a=args[name]
 if name=='RunCode':
  if 'error(' in a['command']:raise RuntimeError('Studio: '+a['command'])
  return 'printed: '+a['command']
 if name=='GetStudioMode':return 'stop'
 if name=='RunScriptInPlayMode':return json.dumps({'success':True,'value':a['code'],'timeout':a['timeout']})
 return name+' ok '+json.dumps(a)
def loop():
 while True:
  try:
   with urllib.request.urlopen(URI+'/request',timeout=30) as r:body=json.loads(r.read())
  except urllib.error.HTTPError as e:
   if e.code==423:continue
   time.sleep(1);continue
  except Exception:time.sleep(1);continue
  try:ok,resp=True,tool(body['args'])
  except Exception as e:ok,resp=False,str(e)
  req=urllib.request.Request(URI+'/response',json.dumps({'success':ok,'response':resp,'id':body['id']}).encode(),{'Content-Type':'application/json'})
  urllib.request.urlopen(req).read()
loop()
