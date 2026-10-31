# Talks to the server like Claude Code: newline-delimited JSON-RPC on its stdin/stdout.
import json,subprocess,sys,time
p=subprocess.Popen([sys.executable,'-I',sys.argv[1]],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,bufsize=1)
n=0
def call(method,params=None,notify=False):
 global n;n+=1;m={'jsonrpc':'2.0','method':method}
 if params is not None:m['params']=params
 if not notify:m['id']=n
 p.stdin.write(json.dumps(m)+'\n');p.stdin.flush()
 if notify:return None
 return json.loads(p.stdout.readline())
r=call('initialize',{'protocolVersion':'2025-06-18','capabilities':{},'clientInfo':{'name':'test','version':'1'}})
print('initialize:',r['result']['serverInfo']['name'],r['result']['protocolVersion'],'| tools capability:','tools' in r['result']['capabilities'])
call('notifications/initialized',notify=True)
r=call('tools/list');print('tools:',[t['name'] for t in r['result']['tools']])
for name,args in [('run_code',{'command':'print(workspace.Name)'}),('get_studio_mode',{}),('run_script_in_play_mode',{'code':'print(1)','mode':'start_play'}),('run_code',{'command':'error("boom")'})]:
 t=time.time();r=call('tools/call',{'name':name,'arguments':args});res=r['result']
 print('%-24s isError=%-5s %.1fs  %s'%(name,res['isError'],time.time()-t,res['content'][0]['text'][:70]))
print('missing argument:',call('tools/call',{'name':'run_code','arguments':{}})['error']['message'])
print('unknown tool:',call('tools/call',{'name':'nope','arguments':{}})['error']['message'])
print('unknown method:',call('resources/list')['error']['code'])
print('ping:',call('ping')['result'])
p.stdin.close();p.wait(10);print('server exited:',p.returncode)
