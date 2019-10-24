'use strict';

var os = require('os');
var nodeStatic = require('node-static');
var https = require('https');
var socketIO = require('socket.io');
var fs = require("fs");
var options = {
	key: fs.readFileSync('key.pem'),
	cert: fs.readFileSync('cert.pem')
};

var port = 1794;
var fileServer = new(nodeStatic.Server)();
var app = https.createServer(options,function(req, res) {
	fileServer.serve(req, res);
}).listen(port);

console.log("Server is listening on port " + port + " ...");

var io = socketIO.listen(app, {
	pingInterval: 10000,
	pingTimeout: 2000
});
io.sockets.on('connection', function(socket) {
	
	// When client connect to server, making a information of its id
	socket.emit('id', socket.id);	
	
	// convenience function to log server messages on the client
	function log() {
		var array = [];
		array.push.apply(array, arguments);
		console.log(array[0], array[1]);
		socket.emit('log', array);
	}

	socket.on('message', function(room, message) {
		log('[Client ' + socket.id + ']', message);
		socket.broadcast.to(room).emit('message', message);
	});

	socket.on('join', function(room, isReconnect) {
		log('[Server]', 
			'Received request from Client ' + socket.id + ' to create or join room \"' + room + '\"');
		
		var room_object = io.sockets.adapter.rooms[room];
		var numClients;
		if (room_object) {
			numClients = Object.keys(room_object).length;
		}   

		if (!room_object) {
			socket.join(room);
			log('[Server]', 'Client ' + socket.id + ' created room \"' + room + '\"');
			socket.emit('created');
			room_object = io.sockets.adapter.rooms[room];
			numClients = Object.keys(room_object).length;
			log('[Server]', 'Room \"' + room + '\" now has ' + numClients + ' client(s)');

		} else{
			log('[Server]', 'Client ' + socket.id + ' joined room \"' + room + '\"');
			io.sockets.in(room).emit('join');
			socket.join(room);
			if(!isReconnect) socket.emit('joined');
			numClients = Object.keys(room_object).length;
			log('[Server]', 'Room \"' + room + '\" now has ' + numClients + ' client(s)');
		}
	});

	socket.on('bye', function(room) {
		log('[Server]', 'Client ' + socket.id + ' left room ' + room);
		socket.broadcast.to(room).emit('bye', socket.id);
	});

	socket.on('disconnecting', (reason) => {
		log('[Server]', 'Client ' + socket.id + ' disconnecting');
	});

	socket.on('disconnect', (reason) => {
		log('[Server]', 'Client ' + socket.id + ' disconnect');
	});

});
