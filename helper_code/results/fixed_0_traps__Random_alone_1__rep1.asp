Feb 26, 2026 4:54:22 PM jade.core.Runtime beginContainer
INFO: ----------------------------------
    This is JADE 4.6.0 - revision 6869 of 30-11-2022 14:47:03
    downloaded in Open Source, under LGPL restrictions,
    at http://jade.tilab.com/
----------------------------------------
Feb 26, 2026 4:54:22 PM jade.imtp.leap.CommandDispatcher addICP
WARNING: Error adding ICP jade.imtp.leap.JICP.JICPPeer@5c8da962[Cannot bind server socket to localhost port 1100].
Feb 26, 2026 4:54:22 PM jade.core.AgentContainerImpl joinPlatform
SEVERE: Communication failure while joining agent platform: No ICP active
jade.core.IMTPException: No ICP active
	at jade.imtp.leap.LEAPIMTPManager.initialize(LEAPIMTPManager.java:138)
	at jade.core.AgentContainerImpl.init(AgentContainerImpl.java:321)
	at jade.core.AgentContainerImpl.joinPlatform(AgentContainerImpl.java:500)
	at jade.core.Runtime.createMainContainer(Runtime.java:159)
	at jade.Boot.main(Boot.java:89)
Feb 26, 2026 4:54:22 PM jade.core.Runtime$1 run
INFO: JADE is closing down now.
